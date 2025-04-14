// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver.webservice.routing

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringDecoder

internal class Routing(private val uriPrefix: String) {
  private val handlers = mutableListOf<Handler>()

  fun get(path: String, handler: RequestContext.() -> Any) {
    handlers.add(PathHandler(HttpMethod.GET, uriPrefix + path, handler))
  }

  fun post(path: String, handler: RequestContext.() -> Any) {
    handlers.add(PathHandler(HttpMethod.POST, uriPrefix + path, handler))
  }

  fun static(path: String) {
    handlers.add(StaticHandler(uriPrefix + path))
  }

  fun handleRequest(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Any {
    return handlers.firstOrNull { it.match(request) }?.execute(urlDecoder, request, context)
           ?: throw CantFindRouteException(request)
  }
}

internal class RequestContext(val urlDecoder: QueryStringDecoder,
                              val request: FullHttpRequest,
                              val context: ChannelHandlerContext,
                              val pathParameters: Map<String, String>)

internal fun route(uriPrefix: String, setup: Routing.() -> Unit): Routing {
  return Routing(uriPrefix).apply(setup)
}

internal class CantFindRouteException(request: FullHttpRequest) : IllegalArgumentException(
  "Wrong path ${request.uri()} or Http method ${request.method().name()}")
