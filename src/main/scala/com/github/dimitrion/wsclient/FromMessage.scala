package com.github.dimitrion.wsclient

import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.Materializer
import play.api.libs.json.{JsValue, Json, Reads}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

trait FromMessage[T] {
  def apply(msg: Message): Future[T]
}

object FromMessage {

  implicit val messageFromMessage: FromMessage[Message] = FromMessage { msg =>
    Future.successful(identity(msg))
  }

  implicit def stringFromMessage(implicit mat: Materializer): FromMessage[String] = FromMessage {
    case TextMessage.Strict(str)      => Future.successful(str)
    case TextMessage.Streamed(stream) => stream.runFold("")(_ + _)

    case other => Future.failed {
      new Exception(s"cannot convert $other to string") with NoStackTrace
    }
  }

  implicit def jsValueFromMessage(implicit mat: Materializer, ec: ExecutionContext): FromMessage[JsValue] =
    FromMessage { msg =>
      for {
        str <- stringFromMessage(mat)(msg)
        json <- Future {Json.parse(str)}
      } yield json
    }

  implicit def jsonFromMessage[T: Reads](implicit mat: Materializer, ec: ExecutionContext): FromMessage[T] =
    FromMessage { msg =>
      for {
        json <- jsValueFromMessage(mat, ec)(msg)
        obj <- Future {json.as[T]}
      } yield obj
    }

  def apply[T](f: Message => Future[T]): FromMessage[T] = new FromMessage[T] {
    def apply(msg: Message): Future[T] = f(msg)
  }
}
