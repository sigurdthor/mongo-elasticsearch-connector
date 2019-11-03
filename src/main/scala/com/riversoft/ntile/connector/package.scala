package com.riversoft.ntile

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol.{jsonFormat2, jsonFormat3}
import spray.json.RootJsonWriter
import spray.json.DefaultJsonProtocol._
import spray.json._

package object connector {

  val logger = Logger(LoggerFactory.getLogger("connector"))

  sealed trait ElementType {
    def name: String
  }

  case object Tag extends ElementType {
    override def name = "tag"
  }

  case object Element extends ElementType {
    override def name = "element"
  }

  case object Attribute extends ElementType {
    override def name = "attribute"
  }

  case object Unknown extends ElementType {
    override def name = ???
  }

  sealed trait ElasticEntity

  case class TagEntity(title: String, spaceUid: Option[String]) extends ElasticEntity

  case class ElementEntity(title: String, spaceUid: Option[String], tags: List[String]) extends ElasticEntity

  case class AttributeEntity(title: String, spaceUid: Option[String], tagUid: Option[String]) extends ElasticEntity

  case object NoEntity extends ElasticEntity

  case class ExtractedDocument(id: String, `type`: ElementType, entity: ElasticEntity)

  object ElasticEntity {

    implicit val tagEntityFormat = jsonFormat2(TagEntity)
    implicit val elementEntityFormat = jsonFormat3(ElementEntity)
    implicit val attributeEntityFormat = jsonFormat3(AttributeEntity)

    implicit object ElasticEntityWriter extends RootJsonWriter[ElasticEntity] {

      def write(p: ElasticEntity) = p match {
        case tag: TagEntity => tag.toJson
        case e: ElementEntity => e.toJson
        case a: AttributeEntity => a.toJson
        case _ => spray.json.JsNull
      }
    }

  }

}
