package com.github.fntz.omhs.test

import com.github.fntz.omhs._
import com.github.fntz.omhs.methods.Methods._
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.ChannelFutureListener
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http._
import io.netty.handler.logging.{LogLevel, LoggingHandler}
import io.netty.util.CharsetUtil
import org.specs2.mutable.Specification
import org.specs2.specification.{AfterAll, Scope}

import java.util.UUID
import play.api.libs.json._

class RoutingSpecs extends Specification with AfterAll {

  import DefaultHttpHandler._
  import ParamDSL._
  import com.github.fntz.omhs.p._
  import AsyncResult.Implicits._

  case class Foo(id: Int)
  implicit val fooReader: BodyReader[Foo] = (str: String) => {
    println("$"*100)
    println(str)
    Json.parse(str).as[Foo](Json.reads[Foo])
  }

  private val channels = scala.collection.mutable.ArrayBuffer[EmbeddedChannel]()
  private val headerName = "X-foo"
  private val headerValue = "bar"


  "routing" in {
    val r1 = get("test" / StringParam) ~> { (x: String) => x }

    "match string" in new RouteTest(r1, "/test/foo") {
      status ==== HttpResponseStatus.OK
      content ==== "foo"
    }

    val r2 = get("test" / LongParam) ~> { (x: Long) => s"$x" }
    "match long" in new RouteTest(r2, "/test/123") {
      status ==== HttpResponseStatus.OK
      content ==== "123"
    }

    val uuid = UUID.randomUUID()
    val r3 = get("test" / UUIDParam) ~> {(x: UUID) => s"$x"}
    "match uuid"  in new RouteTest(r3, s"/test/$uuid") {
      status ==== HttpResponseStatus.OK
      content ==== s"$uuid"
    }

    val r4 = get("test" / RegexParam("^foo".r)) ~> { (x: String) => s"$x" }
    "match regex/200" in new RouteTest(r4, "/test/foobar") {
      status ==== HttpResponseStatus.OK
      content ==== "foo"
    }

    "match regex/404" in new RouteTest(r4, "/test/asd") {
      status ==== HttpResponseStatus.NOT_FOUND
    }

    val r5 = get("test" / *) ~> { (xs: List[String]) => xs.mkString(".") }
    "match tail" in new RouteTest(r5, s"/test/123/foo/bar/$uuid") {
      status ==== HttpResponseStatus.OK
      content ==== s"123.foo.bar.$uuid"
    }

    val r6 = get("test" / HeaderParam(headerName)) ~> { (x: String) => x }
    "match header" in new RouteTest(r6, "/test") {
      override def makeRequest(path: String): DefaultFullHttpRequest = {
        val r =  req(path)
        r.headers().set(headerName, headerValue)
        r
      }
      status ==== HttpResponseStatus.OK
      content ==== headerValue
    }

    def write(x: Foo): String = Json.toJson(x)(Json.writes[Foo]).toString
    val r7 = post("test" / BodyParam[Foo]) ~> { (x: Foo) =>
      write(x)
    }
    val foo = Foo(1000)
    "fetch body" in new RouteTest(r7, "/test") {
      override def makeRequest(path: String): DefaultFullHttpRequest = {
        val r = req(path)
        r.setMethod(HttpMethod.POST)
        val z = r.replace(Unpooled.copiedBuffer(write(foo).getBytes(CharsetUtil.UTF_8)))
        z.asInstanceOf[DefaultFullHttpRequest]
      }
      status ==== HttpResponseStatus.OK
      content ==== write(foo)
    }

    "fetch files" in {
      pending
    }

    val r9 = put("test" / BodyParam[Foo]) ~> {() => ""}
    "unparsed body" in new RouteTest(r9, "/test") {
      override def makeRequest(path: String): DefaultFullHttpRequest = {
        val r = req(path)
        r.setMethod(HttpMethod.PUT)
        r
      }
      status ==== HttpResponseStatus.BAD_REQUEST
      content ==== "body is incorrect"
    }

    "unparsed files" in {
      pending
    }

    val r11 = get("test" / HeaderParam(headerValue)) ~> {() => "ok"}
    "header is missing" in new RouteTest(r11, "/test") {
      status ==== HttpResponseStatus.BAD_REQUEST
      content must contain("")
    }

    val r12 = get("test" / LongParam) ~> {() => "ok" }
    "path is not matched" in new RouteTest(r12, "/test/foo") {
      status ==== HttpResponseStatus.NOT_FOUND
      content must contain("/test/foo")
    }

    val r13 = get("test" / LongParam) ~> {() =>
      throw new RuntimeException("boom")
      "ok"
    }
    "function throw exception: UnhandledException" in new RouteTest(r13, "test/123") {
      status ==== HttpResponseStatus.INTERNAL_SERVER_ERROR
      content must contain("boom")
    }
  }

  private def req(path: String) = {
    new DefaultFullHttpRequest(
      HttpVersion.HTTP_1_1, HttpMethod.GET, path
    )
  }

  private class RouteTest(rule: RuleAndF, path: String) extends Scope {
    def makeRequest(path: String): DefaultFullHttpRequest = req(path)
    println(s"------------__> ${path}")
    val ro = (new Route).addRule(rule).toHandler
    val channel = new EmbeddedChannel(new LoggingHandler(LogLevel.DEBUG))
    channel.pipeline()
      .addFirst(new LoggingHandler(LogLevel.DEBUG))
      .addLast("codec", new HttpRequestDecoder())
      .addLast("aggregator", new HttpObjectAggregator(512*1024))
      .addLast(ro)
    channels += channel
    val request = makeRequest(path)
    channel.writeInbound(request)
    val response: DefaultFullHttpResponse = channel.readOutbound()
    val status = response.status()
    val content =  response.content().toString(CharsetUtil.UTF_8)
  }

  override def afterAll(): Unit = {
    channels.foreach { ch =>
      ch.close().sync().addListener(ChannelFutureListener.CLOSE)
    }
  }
}
