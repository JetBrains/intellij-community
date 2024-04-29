package com.intellij.tools.ide.starter.bus.shared.server

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.tools.ide.starter.bus.shared.dto.SharedEventDto
import com.intellij.tools.ide.starter.bus.shared.dto.SubscriberDto
import com.intellij.tools.ide.starter.bus.shared.server.services.EventsFlowService
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.BufferedReader
import java.net.BindException
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture


object LocalEventBusServer : EventBusServer {
  override val port: Int = 45654
  private lateinit var eventsFlowService: EventsFlowService
  private val objectMapper = jacksonObjectMapper()
  private lateinit var server: HttpServer

  override fun endServer() {
    if (this::server.isInitialized) {
      server.stop(1)
      println("Server stopped")
    }
  }

  private fun handleException(t: Throwable, exchange: HttpExchange) {
    val response = t.message ?: t.toString()
    exchange.responseHeaders.add("Content-Type", "text/plain")
    exchange.sendResponseHeaders(500, response.length.toLong())
    exchange.responseBody.bufferedWriter().use { writer -> writer.write(response) }
  }

  override fun startServer(): Boolean {
    return try {
      server = HttpServer.create(InetSocketAddress(port), 0)
      eventsFlowService = EventsFlowService()

      server.createContext("/postAndWaitProcessing") { exchange ->
        CompletableFuture.runAsync {
          val json = exchange.requestBody.bufferedReader().use(BufferedReader::readText)
          eventsFlowService.postAndWaitProcessing(objectMapper.readValue(json, SharedEventDto::class.java))
        }
          .thenRun {
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
      println("Server started on port $port")
      true
    }
    catch (bind: BindException) {
      println("Server already running. ${bind.message}")
      false
    }
  }
}