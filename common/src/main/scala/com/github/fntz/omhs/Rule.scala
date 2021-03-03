package com.github.fntz.omhs


import io.netty.handler.codec.http.HttpMethod

import scala.collection.mutable.{ArrayBuffer => AB}

class Rule(val method: HttpMethod) {
  private val paths: AB[PathParam] = new AB[PathParam]()
  private val headers: AB[String] = new AB[String]()

  private var reader: BodyReader[_] = null // todo None instead

  private var isBodyNeeded = false

  private var isCurrentRequestNeeded: Boolean = false

  def params: Vector[PathParam] = paths.toVector

  def currentHeaders: Vector[String] = headers.toVector

  def isParseBody = isBodyNeeded

  def isRunWithRequest = isCurrentRequestNeeded

  def currentReader = reader

  def withRequest(): Rule = {
    isCurrentRequestNeeded = true
    this
  }

  // : Reader
  def body[T]()(implicit reader: BodyReader[T]): Rule = {
    this.reader = reader
    this.isBodyNeeded = true
    this
  }

  def path(x: String): Rule = {
    paths += HardCodedParam(x)
    this
  }

  def path(x: PathParam): Rule = {
    paths += x
    this
  }

  def cookie(x: String): Rule = {
    this
  }

  def header(header: String): Rule = {
    headers += header
    this
  }

  override def toString: String = {
    s"$method ${paths.mkString("/")}"
  }

}

object Get {
  def apply(): Rule = new Rule(HttpMethod.GET)
}
object Post {
  def apply(): Rule = new Rule(HttpMethod.POST)
}
object Put {
  def apply(): Rule = new Rule(HttpMethod.PUT)
}
object Delete {
  def apply(): Rule = new Rule(HttpMethod.DELETE)
}
