package com.intellij.tools.ide.starter.bus.shared.client

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.tools.ide.starter.bus.events.Event
import com.intellij.tools.ide.starter.bus.logger.EventBusLoggerFactory
import com.intellij.tools.ide.starter.bus.shared.dto.SharedEventDto
import com.intellij.tools.ide.starter.bus.shared.dto.SubscriberDto
import com.intellij.tools.ide.starter.bus.shared.server.LocalEventBusServer
import java.net.HttpURLConnection
import java.net.URL
import java.rmi.ServerException
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

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

  private fun sendRequest(method: String, endpoint: String, requestBody: String?): String {
    val url = URL("http://localhost:${server.port}/$endpoint")
    val connection = url.openConnection() as HttpURLConnection
    return try {
      connection.requestMethod = method
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
    catch (e: Exception) {
      throw e
    }
    finally {
      connection.disconnect()
    }
  }

  override fun postAndWaitProcessing(sharedEventDto: SharedEventDto): Boolean {
    LOG.info("Post and wait $sharedEventDto")
    return post("postAndWaitProcessing", objectMapper.writeValueAsString(sharedEventDto)).toBoolean()
  }

  override fun newSubscriber(eventClass: Class<out Event>) {
    val simpleName = eventClass.simpleName
    eventClassesLock.writeLock().withLock {
      eventClasses[simpleName] = eventClass.name
    }

    LOG.info("New subscriber $simpleName")
    post("newSubscriber", objectMapper.writeValueAsString(SubscriberDto(simpleName, PROCESS_ID))).toBoolean()
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
    if (!server.startServer()) {
      try {
        val onStartEvents = getEvents()
        LOG.debug("Events on server start: $onStartEvents")
      }
      catch (t: Throwable) {
        LOG.info("Server is running but we cannot get events")
        throw t
      }
    }
  }
}