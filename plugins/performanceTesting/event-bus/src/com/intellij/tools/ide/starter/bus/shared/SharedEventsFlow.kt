package com.intellij.tools.ide.starter.bus.shared

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.tools.ide.starter.bus.EventsFlow
import com.intellij.tools.ide.starter.bus.events.Event
import com.intellij.tools.ide.starter.bus.shared.client.EventBusServerClient
import com.intellij.tools.ide.starter.bus.shared.dto.SharedEventDto
import kotlinx.coroutines.*
import java.util.*
import kotlin.time.Duration

class SharedEventsFlow(private val client: EventBusServerClient,
                       private val localEventsFlow: EventsFlow) : EventsFlow {
  private val objectMapper = jacksonObjectMapper()

  // Server returns all events. We need to save the count of processed events to avoid re-processing the event
  private val processedEvents = HashMap<String, Int>()
  private var serverJob: Job? = null

  fun endServerProcess() {
    runBlocking { serverJob?.cancelAndJoin() }
    serverJob = null
    client.endServerProcess()
  }

  fun starterServerProcess() {
    client.startServerProcess()
  }

  override fun <EventType : Event> subscribe(eventClass: Class<EventType>,
                                             subscriber: Any,
                                             timeout: Duration,
                                             callback: suspend (event: EventType) -> Unit): Boolean {
    return localEventsFlow.subscribe(eventClass, subscriber, timeout, callback).also {
      if (it) client.newSubscriber(eventClass)
    }
  }

  override fun <T : Event> postAndWaitProcessing(event: T) {
    client.postAndWaitProcessing(
      SharedEventDto(event::class.java.simpleName, UUID.randomUUID().toString(), objectMapper.writeValueAsString(event)))
  }

  fun startServerPolling() {
    if (serverJob == null) {
      serverJob = CoroutineScope(Dispatchers.IO).launch {
        while (true) {
          val allEvents = client.getEvents()
          allEvents.entries.forEach { (eventName, events) ->
            // Drop already processed events
            events?.drop(processedEvents[eventName] ?: 0)?.forEach {
              // Save the event as processed (here and not inside the launch) to avoid rerunning processing before the end of processing
              processedEvents[eventName] = processedEvents.getOrDefault(eventName, 0) + 1
              // Processing events in not main flow to avoid blocking on nested events
              launch {
                try {
                  localEventsFlow.postAndWaitProcessing(it.second)
                }
                catch (e: Throwable) {
                  //can’t throw an exception into the main thread (even if CoroutineExceptionHandler used), so log it
                  println(e.stackTraceToString())
                }
                finally {
                  client.processedEvent(it.first)
                }
              }
            }
          }
          delay(100)
        }
      }
    }
  }

  override fun unsubscribeAll() {
    processedEvents.clear()
    client.clear()
  }
}