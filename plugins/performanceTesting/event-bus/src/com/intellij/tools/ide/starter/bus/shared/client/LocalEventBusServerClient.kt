// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.bus.shared.client

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.tools.ide.starter.bus.events.Event
import com.intellij.tools.ide.starter.bus.logger.EventBusLoggerFactory
import com.intellij.tools.ide.starter.bus.shared.dto.SharedEventDto
import com.intellij.tools.ide.starter.bus.shared.dto.SubscriberDto
import com.intellij.tools.ide.starter.bus.shared.server.LocalEventBusServer
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.rmi.ServerException
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock
import kotlin.time.Duration

private val LOG = EventBusLoggerFactory.getLogger(LocalEventBusServerClient::class.java)

class LocalEventBusServerClient(val server: LocalEventBusServer) : EventBusServerClient {
  private val objectMapper = jacksonObjectMapper()
  private val eventClasses = HashMap<String, String>()
  private val eventClassesLock = ReentrantReadWriteLock()
  private val PROCESS_ID = UUID.randomUUID().toString()

  private fun post(endpoint: String, requestBody: String? = null): String {
    return sendRequest("POST", endpoint, requestBody)
  }

  private fun get(endpoint: String, requestBody: String? = null): String {
    return sendRequest("GET", endpoint, requestBody)
  }

  private fun sendRequest(method: String, endpoint: String, requestBody: String?, retriesOnTheSamePort: Int = 0): String {
    val url = URL("http://localhost:${server.port}/$endpoint")
    val connection = url.openConnection() as HttpURLConnection
    return try {
      connection.requestMethod = method
      connection.connectTimeout = 1000 // 1 seconds
      connection.readTimeout = 5000
      requestBody?.also { body ->
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.bufferedWriter().use {
          it.write(body)
        }
      }

      connection.responseCode.also {
        if (it != HttpURLConnection.HTTP_OK) {
          throw ServerException("Code: $it. ${connection.responseMessage}")
        }
      }

      connection.inputStream.bufferedReader().use {
        it.readText()
      }
    }
    catch (e: ConnectException) {
      if (retriesOnTheSamePort < 3) {
        sendRequest(method, endpoint, requestBody, retriesOnTheSamePort + 1)
      } else {
        if (!server.updatePort()) throw e
        sendRequest(method, endpoint, requestBody, 0)
      }
    }
    finally {
      connection.disconnect()
    }
  }

  override fun postAndWaitProcessing(sharedEventDto: SharedEventDto): Boolean {
    LOG.info("Post and wait $sharedEventDto")
    return post("postAndWaitProcessing", objectMapper.writeValueAsString(sharedEventDto)).toBoolean()
  }

  override fun newSubscriber(eventClass: Class<out Event>, timeout: Duration, subscriberName: String) {
    val simpleName = eventClass.simpleName
    eventClassesLock.writeLock().withLock {
      eventClasses[simpleName] = eventClass.name
    }

    LOG.info("New subscriber $simpleName")
    post("newSubscriber", objectMapper.writeValueAsString(SubscriberDto(subscriberName, simpleName, PROCESS_ID, timeout.inWholeMilliseconds))).toBoolean()
  }

  override fun unsubscribe(eventClass: Class<out Event>, subscriberName: String) {
    val simpleName = eventClass.simpleName
    post("unsubscribe", objectMapper.writeValueAsString(SubscriberDto(subscriberName, simpleName, PROCESS_ID))).toBoolean()
  }

  override fun getEvents(): Map<String, List<Pair<String, Event>>?> {
    val eventType = object : TypeReference<HashMap<String, MutableList<SharedEventDto>>>() {}
    return objectMapper
      .readValue(get("getEvents", PROCESS_ID), eventType)
      .entries.associateBy({ it.key },
                           { entry ->
                             eventClassesLock.readLock().withLock { eventClasses[entry.key] }?.let { className ->
                               val clz = Class.forName(className) as Class<out Event>
                               entry.value.map {
                                 it.eventId to objectMapper.readValue(it.serializedEvent, clz)
                               }
                             }
                           })
  }

  override fun processedEvent(eventName: String) {
    LOG.info("Processed event $eventName")
    post("processedEvent", eventName)
  }

  override fun clear() {
    eventClassesLock.writeLock().withLock { eventClasses.clear() }
    try {
      post("clear")
    }
    catch (t: Throwable) {
      LOG.info("Clear server exception: ${t.message}. $t")
    }
  }

  override fun endServerProcess() {
    server.endServer()
  }

  override fun startServerProcess() {
    server.startServer()
  }
}