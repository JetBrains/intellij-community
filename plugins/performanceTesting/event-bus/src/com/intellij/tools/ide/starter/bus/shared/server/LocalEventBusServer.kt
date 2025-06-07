// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.bus.shared.server

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.tools.ide.starter.bus.logger.EventBusLoggerFactory
import com.intellij.tools.ide.starter.bus.shared.dto.SharedEventDto
import com.intellij.tools.ide.starter.bus.shared.dto.SubscriberDto
import com.intellij.tools.ide.starter.bus.shared.server.services.EventsFlowService
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.BufferedReader
import java.net.BindException
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture

private val LOG = EventBusLoggerFactory.getLogger(LocalEventBusServer::class.java)

object LocalEventBusServer : EventBusServer {
  private val portsPool: List<Int> = (45654..45754 step 10).toList()
  private var currentPortIndex = 0
  private lateinit var eventsFlowService: EventsFlowService
  private val objectMapper = jacksonObjectMapper()
  private lateinit var server: HttpServer

  override val port: Int
    get() = portsPool[currentPortIndex]

  override fun endServer() {
    if (this::server.isInitialized) {
      server.stop(1)
      currentPortIndex = 0
      LOG.info("Server stopped")
    }
  }

  override fun updatePort(): Boolean {
    if (currentPortIndex == portsPool.size - 1) return false
    currentPortIndex++
    return true
  }

  private fun handleException(t: Throwable, exchange: HttpExchange) {
    val response = t.message ?: t.toString()
    exchange.responseHeaders.add("Content-Type", "text/plain")
    exchange.sendResponseHeaders(500, response.length.toLong())
    exchange.responseBody.bufferedWriter().use { writer -> writer.write(response) }
  }

  override fun startServer() {
    return try {
      server = HttpServer.create(InetSocketAddress(port), 0)
      eventsFlowService = EventsFlowService()

      server.createContext("/postAndWaitProcessing") { exchange ->
        CompletableFuture.runAsync {
          LOG.debug("Got postAndWait request")
          val json = exchange.requestBody.bufferedReader().use(BufferedReader::readText)
          eventsFlowService.postAndWaitProcessing(objectMapper.readValue(json, SharedEventDto::class.java))
        }
          .thenRun {
            LOG.debug("Processed postAndWait request")
            val response = "Processed"
            exchange.sendResponseHeaders(200, response.length.toLong())
            exchange.responseBody.bufferedWriter().use { it.write(response) }
          }
          .exceptionally {
            handleException(it, exchange)
            return@exceptionally null
          }
      }

      server.createContext("/newSubscriber") { exchange ->
        exchange.use {
          try {
            val json = exchange.requestBody.bufferedReader().use(BufferedReader::readText)
            val subscriberDto = objectMapper.readValue(json, SubscriberDto::class.java)
            eventsFlowService.newSubscriber(subscriberDto)
            val response = "Crated"
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.bufferedWriter().use { it.write(response) }
          }
          catch (t: Throwable) {
            handleException(t, exchange)
          }
        }
      }

      server.createContext("/unsubscribe") { exchange ->
        exchange.use {
          try {
            val json = exchange.requestBody.bufferedReader().use(BufferedReader::readText)
            val subscriberDto = objectMapper.readValue(json, SubscriberDto::class.java)
            eventsFlowService.unsubscribe(subscriberDto)
            val response = "Unsubscribed"
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.bufferedWriter().use { it.write(response) }
          }
          catch (t: Throwable) {
            handleException(t, exchange)
          }
        }
      }

      server.createContext("/getEvents") { exchange ->
        exchange.use {
          try {
            val eventsJson = exchange.requestBody.use { requestBody ->
              objectMapper.writeValueAsBytes(eventsFlowService.getEvents(requestBody.bufferedReader().readText()))
            }
            exchange.sendResponseHeaders(200, eventsJson.size.toLong())
            exchange.responseBody.use {
              it.write(eventsJson)
            }
          }
          catch (t: Throwable) {
            handleException(t, exchange)
          }
        }
      }

      server.createContext("/processedEvent") { exchange ->
        exchange.use {
          try {
            val response = "Processed"
            exchange.requestBody.use { requestBody ->
              eventsFlowService.processedEvent(requestBody.bufferedReader().readText())
            }
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.bufferedWriter().use { it.write(response) }
          }
          catch (t: Throwable) {
            handleException(t, exchange)
          }
        }
      }

      server.createContext("/clear") { exchange ->
        exchange.use {
          try {
            val response = "Cleared"
            eventsFlowService.clear()
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.bufferedWriter().use { it.write(response) }
          }
          catch (t: Throwable) {
            handleException(t, exchange)
          }
        }
      }

      server.start()
      LOG.info("Server started on port $port")
    }
    catch (bind: BindException) {
      LOG.info("Port $port is busy. Trying use another")
      if (!updatePort()) throw BindException("All ports from ports pool are busy")
      startServer()
    }
  }
}