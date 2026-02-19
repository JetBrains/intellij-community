// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver.webservice.routing

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import java.net.URL

internal interface Handler {
  fun match(request: FullHttpRequest): Boolean
  fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Any
}

internal class StaticHandler(urlPrefix: String) : Handler {
  private val path: String = "/static"
  private val files: Map<String, URL> = listOf(
    "scripts.js", "styles.css", "updateButton.js", "xpathEditor.js", "img/locator.png", "img/show.png"
  ).associate { "$urlPrefix/$it" to javaClass.getResource("$path/$it") }

  override fun match(request: FullHttpRequest): Boolean {
    return files.keys.any { request.uri().endsWith(it) }
  }

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Any {
    val fileName = files.keys.firstOrNull { request.uri().endsWith(it) } ?: throw IllegalArgumentException(
      "wrong file requested ${request.uri()}")
    val fileType = when {
      fileName.endsWith(".css") -> "text/css"
      fileName.endsWith(".js") -> "text/javascript"
      else -> null
    }
    return StaticFile(fileType, files[fileName]!!.readBytes())
  }
}

internal class StaticFile(val type: String?, val byteArray: ByteArray)