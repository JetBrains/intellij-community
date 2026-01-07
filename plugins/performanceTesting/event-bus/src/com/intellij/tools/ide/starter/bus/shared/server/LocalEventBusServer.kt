// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.bus.shared.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.tools.ide.starter.bus.logger.EventBusLoggerFactory
import com.intellij.tools.ide.starter.bus.shared.dto.SharedEventDto
import com.intellij.tools.ide.starter.bus.shared.dto.SubscriberDto
import com.intellij.tools.ide.starter.bus.shared.server.services.EventsFlowService
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.*
import io.netty.util.CharsetUtil
import io.netty.util.concurrent.DefaultThreadFactory
import java.net.BindException
import java.nio.channels.spi.SelectorProvider
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private val LOG = EventBusLoggerFactory.getLogger(LocalEventBusServer::class.java)

object LocalEventBusServer : EventBusServer {
  private val portsPool: List<Int> = (45654..45754 step 10).toList()
  private var currentPortIndex = 0
  private lateinit var eventsFlowService: EventsFlowService
  private val objectMapper = jacksonObjectMapper()
  private var bossGroup: EventLoopGroup? = null
  private var workerGroup: EventLoopGroup? = null
  private var serverChannel: Channel? = null
  private val threadFactory: DefaultThreadFactory = DefaultThreadFactory("${LocalEventBusServer::class.simpleName}Thread")

  override val port: Int
    get() = portsPool[currentPortIndex]

  override fun endServer() {
    serverChannel?.close()?.sync()
    workerGroup?.shutdownGracefully(1, 1, TimeUnit.SECONDS)
    bossGroup?.shutdownGracefully(1, 1, TimeUnit.SECONDS)
    serverChannel = null
    workerGroup = null
    bossGroup = null
    currentPortIndex = 0
    LOG.info("Server stopped")
  }

  override fun updatePort(): Boolean {
    if (currentPortIndex == portsPool.size - 1) return false
    currentPortIndex++
    return true
  }

  override fun startServer() {
    try {
      eventsFlowService = EventsFlowService()

      bossGroup = MultiThreadIoEventLoopGroup(
        1,
        threadFactory,
        NioIoHandler.newFactory(SelectorProvider.provider(), DefaultSelectStrategyFactory.INSTANCE)
      )
      workerGroup = MultiThreadIoEventLoopGroup(
        Runtime.getRuntime().availableProcessors(),
        threadFactory,
        NioIoHandler.newFactory(SelectorProvider.provider(), DefaultSelectStrategyFactory.INSTANCE)
      )

      val bootstrap = ServerBootstrap()
      bootstrap.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel::class.java)
        .childHandler(object : ChannelInitializer<SocketChannel>() {
          override fun initChannel(ch: SocketChannel) {
            ch.pipeline().addLast(HttpServerCodec())
            ch.pipeline().addLast(HttpObjectAggregator(1048576))
            ch.pipeline().addLast(EventBusServerHandler(eventsFlowService, objectMapper))
          }
        })
        .option(ChannelOption.SO_BACKLOG, 128)
        .childOption(ChannelOption.SO_KEEPALIVE, true)

      val future = bootstrap.bind(port).sync()
      serverChannel = future.channel()
      LOG.info("Server started on port $port")
    }
    catch (bind: BindException) {
      LOG.info("Port $port is busy. Trying use another")
      val savedPortIndex = currentPortIndex
      endServer()
      currentPortIndex = savedPortIndex
      if (!updatePort()) throw BindException("All ports from ports pool are busy")
      startServer()
    }
  }
}

@ChannelHandler.Sharable
private class EventBusServerHandler(
  private val eventsFlowService: EventsFlowService,
  private val objectMapper: ObjectMapper,
) : SimpleChannelInboundHandler<FullHttpRequest>() {

  override fun channelRead0(ctx: ChannelHandlerContext, request: FullHttpRequest) {
    val uri = request.uri()
    val path = uri.substringBefore('?')

    // Log the complete incoming request for debugging
    LOG.debug("Incoming request: method=${request.method()}, path=$path, uri=$uri, headers=${request.headers()}")

    try {
      when {
        path == "/postAndWaitProcessing" && request.method() == HttpMethod.POST -> {
          handlePostAndWaitProcessing(ctx, request)
        }
        path == "/newSubscriber" && request.method() == HttpMethod.POST -> {
          handleNewSubscriber(ctx, request)
        }
        path == "/unsubscribe" && request.method() == HttpMethod.POST -> {
          handleUnsubscribe(ctx, request)
        }
        path == "/getEvents" && request.method() == HttpMethod.POST -> {
          handleGetEvents(ctx, request)
        }
        path == "/processedEvent" && request.method() == HttpMethod.POST -> {
          handleProcessedEvent(ctx, request)
        }
        path == "/clear" && request.method() == HttpMethod.POST -> {
          handleClear(ctx, request)
        }
        else -> {
          LOG.error("Unknown endpoint: $path")
          sendError(ctx, HttpResponseStatus.NOT_FOUND, "Endpoint not found")
        }
      }
    }
    catch (t: Throwable) {
      LOG.error("Error handling request: ${t.message}")
      sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, t.message ?: t.toString())
    }
  }

  private fun handlePostAndWaitProcessing(ctx: ChannelHandlerContext, request: FullHttpRequest) {
    val json = request.content().toString(CharsetUtil.UTF_8)

    CompletableFuture.runAsync {
      LOG.debug("Got postAndWait request")
      eventsFlowService.postAndWaitProcessing(objectMapper.readValue(json, SharedEventDto::class.java))
    }
      .thenRun {
        LOG.debug("Processed postAndWait request")
        sendResponse(ctx, "Processed")
      }
      .exceptionally { throwable ->
        sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, throwable.message ?: throwable.toString())
        null
      }
  }

  private fun handleNewSubscriber(ctx: ChannelHandlerContext, request: FullHttpRequest) {
    val json = request.content().toString(CharsetUtil.UTF_8)
    val subscriberDto = objectMapper.readValue(json, SubscriberDto::class.java)
    eventsFlowService.newSubscriber(subscriberDto)
    sendResponse(ctx, "Created")
  }

  private fun handleUnsubscribe(ctx: ChannelHandlerContext, request: FullHttpRequest) {
    val json = request.content().toString(CharsetUtil.UTF_8)
    val subscriberDto = objectMapper.readValue(json, SubscriberDto::class.java)
    eventsFlowService.unsubscribe(subscriberDto)
    sendResponse(ctx, "Unsubscribed")
  }

  private fun handleGetEvents(ctx: ChannelHandlerContext, request: FullHttpRequest) {
    val processId = request.content().toString(CharsetUtil.UTF_8)
    val eventsJson = objectMapper.writeValueAsBytes(eventsFlowService.getEvents(processId))
    sendJsonResponse(ctx, eventsJson)
  }

  private fun handleProcessedEvent(ctx: ChannelHandlerContext, request: FullHttpRequest) {
    val eventName = request.content().toString(CharsetUtil.UTF_8)
    eventsFlowService.processedEvent(eventName)
    sendResponse(ctx, "Processed")
  }

  private fun handleClear(ctx: ChannelHandlerContext, request: FullHttpRequest) {
    eventsFlowService.clear()
    sendResponse(ctx, "Cleared")
  }

  private fun sendResponse(ctx: ChannelHandlerContext, message: String) {
    val content = Unpooled.copiedBuffer(message, CharsetUtil.UTF_8)
    val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content)
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())
    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
  }

  private fun sendJsonResponse(ctx: ChannelHandlerContext, jsonBytes: ByteArray) {
    val content = Unpooled.copiedBuffer(jsonBytes)
    val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content)
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8")
    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())
    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
  }

  private fun sendError(ctx: ChannelHandlerContext, status: HttpResponseStatus, message: String) {
    val content = Unpooled.copiedBuffer(message, CharsetUtil.UTF_8)
    val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content)
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())
    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
  }

  override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
    LOG.error("Exception in channel $cause")
    ctx.close()
  }
}