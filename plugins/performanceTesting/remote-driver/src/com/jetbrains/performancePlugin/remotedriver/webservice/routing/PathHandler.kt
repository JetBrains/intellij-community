// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver.webservice.routing

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringDecoder


internal class PathHandler(private val method: HttpMethod, path: String, private val handler: RequestContext.() -> Any) : Handler {
  private val pathSegments: List<PathSegment> = mutableListOf<PathSegment>().apply {
    path.split("/").filter { it.isNotEmpty() }.forEach { segment ->
      val type = if (segment.startsWith("{") && segment.endsWith("}")) PathSegmentType.PARAMETER else PathSegmentType.CONSTANT
      val value = if (type == PathSegmentType.PARAMETER) segment.substringAfter("{").substringBeforeLast("}") else segment
      add(PathSegment(type, value))
    }
  }.toList()

  override fun match(request: FullHttpRequest): Boolean {
    if (request.method() != this.method) return false
    val uriSegments = request.uri().substringBefore("?").split("/").filter { it.isNotEmpty() }
    if (uriSegments.size != pathSegments.size) return false
    for (n in (uriSegments.indices)) {
      if (pathSegments[n].type != PathSegmentType.PARAMETER && pathSegments[n].value != uriSegments[n]) return false
    }
    return true
  }

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Any {
    val uriSegments = request.uri().substringBefore("?").split("/").filter { it.isNotEmpty() }
    if (uriSegments.size != pathSegments.size) throw IllegalStateException("Wrong handler, uri path segments are not match handler path")
    val pathParameters = mutableMapOf<String, String>()
    for (n in (uriSegments.indices)) {
      if (pathSegments[n].type == PathSegmentType.PARAMETER) pathParameters[pathSegments[n].value] = uriSegments[n]
    }
    mutableMapOf<String, String>()
    request.content()
    return RequestContext(urlDecoder, request, context, pathParameters).handler()
  }
}

internal class PathSegment(val type: PathSegmentType, val value: String)

internal enum class PathSegmentType { CONSTANT, PARAMETER }