package com.github.dimitrion.wsclient

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.ws._
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import akka.{Done, NotUsed}
import com.typesafe.config.ConfigFactory

import scala.concurrent.{Future, Promise}
import scala.util.control.NoStackTrace
import scala.util.{Failure, Success}

trait WebSocketClient[-Out] {
  def send(msg: Out): Future[Done]
  def close(): Unit
}

object WebSocketClient {

  val bufferSize: Int = ConfigFactory.load().getInt("com.gitlab.dimitrion.wsclient.buffer-size")

  type OnMessage[-In] = In => Unit
  type OnClosed = () => Unit

  def connect[In: FromMessage, Out: ToMessage](uri: Uri)
    (onMessage: OnMessage[In], onClosed: OnClosed)
    (implicit system: ActorSystem, mat: Materializer): Future[WebSocketClient[Out]] = {

    connect(WebSocketRequest(uri))(onMessage, onClosed)
  }

  def connect[In: FromMessage, Out: ToMessage](request: WebSocketRequest)
    (onMessage: OnMessage[In], onClosed: OnClosed)
    (implicit system: ActorSystem, mat: Materializer): Future[WebSocketClient[Out]] = {

    import system.dispatcher

    val fromMessage = implicitly[FromMessage[In]]
    val toMessage = implicitly[ToMessage[Out]]

    val (_, incoming: Sink[Message, NotUsed]) = Sink.foreach[Message] { msg =>
      fromMessage(msg) onComplete {
        case Success(x) => onMessage(x)
        case Failure(e) => //TODO what?
      }
    }.preMaterialize()

    val (queue, outgoing) = Source.queue[Message](bufferSize, OverflowStrategy.fail).preMaterialize()
    queue.watchCompletion() map { _ => onClosed() }

    val wsFlow = Http().webSocketClientFlow(request)

    val upgrading: Future[WebSocketUpgradeResponse] = outgoing
      .viaMat(wsFlow)(Keep.right)
      .toMat(incoming)(Keep.left)
      .run()

    val promise = Promise[WebSocketClient[Out]]()

    val client = new WebSocketClient[Out] {
      def send(msg: Out) = queue.offer(toMessage(msg)) map (_ => Done)
      def close() = queue.complete()
    }

    upgrading onComplete {
      case Success(_: ValidUpgrade)           => promise.success(client)
      case Success(x: InvalidUpgradeResponse) => promise.failure(new Exception(s"Bad response: $x") with NoStackTrace)
      case Failure(e)                         => promise.failure(e)
    }

    promise.future
  }
}
