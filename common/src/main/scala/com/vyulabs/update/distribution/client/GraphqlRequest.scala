package com.vyulabs.update.distribution.client

import spray.json._
import spray.json.DefaultJsonProtocol._

import scala.reflect.ClassTag

case class GraphqlArgument(name: String, value: JsValue, inputType: String)

object GraphqlArgument {
  def apply[T](arg : (String, T), inputType: String = "")
              (implicit classTag: ClassTag[T], writer: JsonWriter[T]): GraphqlArgument = {
    GraphqlArgument(arg._1, arg._2.toJson,
      if (inputType.isEmpty) {
        arg._2 match {
          case value: Option[_] =>
            value match {
              case Some(obj) =>
                obj.getClass.getSimpleName
              case None =>
                "String"
            }
          case _ =>
            arg._2.getClass.getSimpleName
        }
      } else inputType)
  }
}

case class GraphqlRequest[Response, ResponseItem](request: String, command: String, arguments: Seq[GraphqlArgument] = Seq.empty, subSelection: String = "")
                                                 (implicit itemClassTag: ClassTag[ResponseItem], reader: JsonReader[Response]) {
  def encodeRequest(): JsObject = {
    val types = arguments.foldLeft("")((args, arg) => {
      args + (if (!args.isEmpty) ", " else "") + s"$$${arg.name}: ${arg.inputType}!"
    })
    val args = arguments.foldLeft("")((args, arg) => {
      args + (if (!args.isEmpty) ", " else "") + s"${arg.name}: $$${arg.name}"
    })
    val query = s"${request} ${command}(${types}) { ${command} (${args}) ${subSelection} }"
    val variables = arguments.foldLeft(Map.empty[String, JsValue])((map, arg) => map + (arg.name -> arg.value))
    JsObject("query" -> JsString(query), "variables" -> variables.toJson)
  }

  def decodeResponse(responseJson: JsObject): Either[Response, String] = {
    val fields = responseJson.fields
    fields.get("data") match {
      case Some(data) if (data != JsNull) =>
        val response = data.asJsObject.fields.get(command).getOrElse(
          return Right(s"No field ${command} in the response data: ${data}"))
        Left(response.convertTo[Response])
      case _ =>
        fields.get("errors") match {
          case Some(errors) =>
            Right(s"Graphql request error: ${errors}")
          case None =>
            Right(s"Graphql invalid response: ${responseJson}")
        }
    }
  }
}

object GraphqlQuery {
  def apply[Response](command: String, arguments: Seq[GraphqlArgument] = Seq.empty)
                     (implicit itemClassTag: ClassTag[Response], reader: JsonReader[Response]) = {
    GraphqlRequest[Response, Response]("query", command, arguments)
  }
}

object GraphqlQueryList {
  def apply[ResponseItem](command: String, arguments: Seq[GraphqlArgument] = Seq.empty, subSelection: String = "")
                         (implicit itemClassTag: ClassTag[ResponseItem], reader: JsonReader[Seq[ResponseItem]]) = {
    GraphqlRequest[Seq[ResponseItem], ResponseItem]("query", command, arguments, subSelection)
  }
}

object GraphqlMutation {
  def apply(command: String, arguments: Seq[GraphqlArgument] = Seq.empty) = {
    GraphqlRequest[Boolean, Boolean]("mutation", command, arguments)
  }
}