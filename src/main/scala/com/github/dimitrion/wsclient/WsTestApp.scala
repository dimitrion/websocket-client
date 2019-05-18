package com.github.dimitrion.wsclient

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn

object WsTestApp extends App {

  implicit val sys = ActorSystem()
  implicit val mat = ActorMaterializer()

  val ws = Await.result(
    WebSocketClient.connect[String, String](
      uri = "wss://echo.websocket.org")(
      onMessage = receive,
      onClosed = { () => closed }
    ),
    10.seconds
  )

  def receive(msg: String) = println(s"<<< $msg")
  def closed() = println(s"!!!! closed")

  var line = StdIn.readLine()
  while (!line.isEmpty) {
    println(s">>> $line")
    ws.send(line)
    line = StdIn.readLine()
  }
  println("closing")
  ws.close()

  Await.ready(sys.terminate(), 10.seconds)
}
