package com.github.dimitrion.wsclient

import akka.http.scaladsl.model.ws.{Message, TextMessage}
import play.api.libs.json.{JsValue, Json, Writes}

trait ToMessage[-T] {
  def apply(msg: T): Message
}

object ToMessage {

  implicit val messageToMessage: ToMessage[Message] = ToMessage {identity}
  implicit val stringToMessage: ToMessage[String] = ToMessage { str => TextMessage.Strict(str) }

  implicit val jsValueToMessage: ToMessage[JsValue] = ToMessage { json =>
    TextMessage.Strict(Json.stringify(json))
  }

  implicit def jsonToMessage[T: Writes]: ToMessage[T] = ToMessage { x =>
    val json = Json.toJson(x)
    jsValueToMessage(json)
  }

  def apply[T](f: T => Message): ToMessage[T] = new ToMessage[T] {
    def apply(msg: T) = f(msg)
  }
}
