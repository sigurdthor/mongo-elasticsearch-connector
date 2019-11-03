package com.riversoft.ntile.connector

import akka.actor.ActorSystem
import akka.stream.alpakka.elasticsearch.IncomingMessage
import akka.stream.alpakka.elasticsearch.scaladsl.ElasticsearchSink
import akka.stream.scaladsl.{Keep, PartitionHub}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import org.elasticsearch.client.RestClient
import play.api.libs.json._
import reactivemongo.akkastream.cursorProducer
import reactivemongo.api._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try


object Main extends App {

  //Workaround wait until elastic will be ready
  Thread.sleep(30000)

  val operations = "i" :: "u" :: Nil
  val collections = Map("protontile.elements" -> Element, "protontile.tags" -> Tag)
  val partitions = Tag :: Element :: Nil

  val decider: Supervision.Decider = {
    case e: Throwable => logger.error(e.getMessage, e)
      Supervision.Resume
  }

  implicit val system = ActorSystem("MongoOplogTailer")
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))
  val connection = Config.getMongoConnection
  implicit val client: RestClient = Config.getElasticClient()

  val oplogF: Future[BSONCollection] = connection.map(_.collection("oplog.$main"))

  val sourceF = for {
    oplog <- oplogF
  } yield oplog
    .find(Json.obj("op" -> Json.obj("$in" -> operations)))
    .options(QueryOpts().tailable.awaitData.noCursorTimeout)
    .cursor[BSONDocument]()
    .documentSource()

  val handledDocs = for {
    source <- sourceF
  } yield source
    .map(BSONFormats.writeAsJsValue)
    .filter(isAppropriateRecord)
    .map(handleDocument)
    .toMat(PartitionHub.sink(
      (_, elem) => partitions.indexOf(elem.`type`),
      startAfterNrOfConsumers = 0,
      bufferSize = 256))(Keep.right).run()
    .map { doc => logger.info(s"Prepared entity ${doc.entity} for elastic indexing"); doc }
    .map { doc => IncomingMessage(Some(doc.id), doc.entity) }

  for {
    d <- handledDocs
  } yield {
    d.runWith(
      ElasticsearchSink.create[ElasticEntity](
        indexName = "ntile_tag",
        typeName = Tag.name
      )
    )
    d.runWith(
      ElasticsearchSink.create[ElasticEntity](
        indexName = "ntile_element",
        typeName = Element.name
      )
    )
  }

  private def isAppropriateRecord(doc: JsValue): Boolean = {
    collections.contains((doc \ "ns").as[String]) && (doc \ "o" \ "_id").isDefined && (doc \ "o" \ "title").isDefined
  }

  private def handleDocument(jsonDoc: play.api.libs.json.JsValue) = {
    val obj = jsonDoc \ "o"

    logger.debug(s"Processing object $obj")

    val id = (obj \ "_id" \ "$oid").as[String]
    val title = (obj \ "title").as[String].trim
    val objType = collections.getOrElse((jsonDoc \ "ns").as[String], Unknown)
    val spaceUid = (obj \ "space").asOpt[String]

    val entity = objType match {

      case Tag => TagEntity(title, spaceUid)

      case Element =>
        val maybeTags = Try((obj \ "tags").as[List[String]])
        ElementEntity(title, spaceUid, maybeTags.getOrElse(List()))

      case _ => NoEntity
    }
    ExtractedDocument(id, objType, entity)
  }
}

