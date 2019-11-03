package com.riversoft.ntile.connector

import java.util.Collections.emptyMap

import com.typesafe.config.ConfigFactory
import org.apache.http.HttpHost
import org.apache.http.entity.ContentType
import org.apache.http.nio.entity.NStringEntity
import org.elasticsearch.client.{ResponseException, RestClient}
import org.slf4j.LoggerFactory
import reactivemongo.api.{DefaultDB, FailoverStrategy, MongoConnection, MongoDriver}

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object Config {

  val config = ConfigFactory.load()
  val logger = LoggerFactory.getLogger("Connector config")

  @tailrec
  def getElasticClient(numRetry: Int = 0): RestClient = {

    if (numRetry > 60) throw new IllegalStateException("Couldn't connect to elasticsearch")

    try {
      val client = RestClient
        .builder(new HttpHost(config.getString("elasticsearch.host"), 9200))
        .build()

      updateIndexSettings(client)
      client
    } catch {
      case e: Throwable =>
        Thread.sleep(1000)
        getElasticClient(numRetry + 1)
    }

  }

  private def updateIndexSettings(client: RestClient) = {

    def createIndex(name: String) = {
      try {
        client.performRequest("PUT", s"/ntile_${name}", emptyMap[String, String]())
      } catch {
        case e: ResponseException => logger.info("Index already exists", e)
        case e: Throwable => logger.error(e.getMessage)
      }
    }

    val tagSettings = new NStringEntity(
      """{
            "analysis": {
              "analyzer": {
               "keyword_lowercase": {
                   "type": "custom",
                   "tokenizer": "keyword",
                   "filter": ["lowercase"]
                 }
            }
          }
        }
      """, ContentType.APPLICATION_JSON)

    val elementSettings = new NStringEntity(
      """{
            "analysis": {
              "analyzer": {
               "standard_lowercase": {
                  "type": "custom",
                  "tokenizer": "standard",
                  "filter": ["lowercase"]
                }
            }
          }
        }
      """, ContentType.APPLICATION_JSON)

    createIndex("element")
    createIndex("tag")


    client.performRequest("POST", "/_all/_close")
    client.performRequest("PUT", "/ntile_element/_settings", emptyMap[String, String](), elementSettings)
    client.performRequest("PUT", "/ntile_tag/_settings", emptyMap[String, String](), tagSettings)
    client.performRequest("POST", "/_all/_open")
  }


  def getMongoConnection()(implicit ec: ExecutionContext): Future[DefaultDB] = {

    val driver = new MongoDriver
    val parsedUri = MongoConnection.parseURI(s"mongodb://${config.getString("mongo.host")}:27017/local")

    val customStrategy = FailoverStrategy(
      initialDelay = 500 milliseconds,
      retries = 5,
      delayFactor =
        attemptNumber => 1 + attemptNumber * 0.5
    )

    for {
      uri <- Future.fromTry(parsedUri)
      con = driver.connection(uri)
      dn <- Future(uri.db.get)
      db <- con.database(dn, customStrategy)
    } yield db
  }
}
