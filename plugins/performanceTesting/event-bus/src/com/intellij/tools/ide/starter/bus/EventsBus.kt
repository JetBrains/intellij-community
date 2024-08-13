package com.intellij.tools.ide.starter.bus

import com.intellij.tools.ide.starter.bus.events.Event
import com.intellij.tools.ide.starter.bus.local.LocalEventsFlow
import com.intellij.tools.ide.starter.bus.logger.EventBusLoggerFactory
import com.intellij.tools.ide.starter.bus.shared.SharedEventsFlow
import com.intellij.tools.ide.starter.bus.shared.client.LocalEventBusServerClient
import com.intellij.tools.ide.starter.bus.shared.events.SharedEvent
import com.intellij.tools.ide.starter.bus.shared.server.LocalEventBusServer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

object EventsBus {
  val LOG = EventBusLoggerFactory.getLogger(EventsBus::class.java)
  val EVENTS_FLOW = LocalEventsFlow()
  val SHARED_EVENTS_FLOW = SharedEventsFlow(LocalEventBusServerClient(LocalEventBusServer), EVENTS_FLOW)

  fun executeWithExceptionHandling(ignoreExceptions: Boolean = true, block: () -> Unit) {
    runCatching { block() }.onFailure { t ->
        if (ignoreExceptions) {
          LOG.info("Ignored: $t")
        } else {
          throw t
        }
      }
  }

  /**
   *  Different events can be processed in parallel
   */
  fun <T : Event> postAndWaitProcessing(event: T, ignoreExceptions: Boolean = true) {
    executeWithExceptionHandling(ignoreExceptions) {
      if ((event is SharedEvent))
        SHARED_EVENTS_FLOW.postAndWaitProcessing(event)
      else
        EVENTS_FLOW.postAndWaitProcessing(event)
    }
  }

  /**
   * Can have only one subscription by pair subscriber + event
   * Subscriber might be invoked multiple times on different events since unsubscription happens only after end of test.
   * If you need to unsubscribe earlier call [unsubscribe]
   */
  inline fun <reified EventType : Event> subscribe(
    subscriber: Any,
    timeout: Duration = 2.minutes,
    ignoreExceptions: Boolean = true,
    noinline callback: suspend (event: EventType) -> Unit
  ): EventsBus {
    executeWithExceptionHandling(ignoreExceptions) {
      if (SharedEvent::class.java.isAssignableFrom(EventType::class.java)) {
        SHARED_EVENTS_FLOW.subscribe(eventClass = EventType::class.java, subscriber = subscriber, timeout, callback)
        SHARED_EVENTS_FLOW.startServerPolling()
      }
      else
        EVENTS_FLOW.subscribe(eventClass = EventType::class.java, subscriber = subscriber, timeout, callback)
    }
    return this
  }

  inline fun <reified EventType : Event> unsubscribe(subscriber: Any, ignoreExceptions: Boolean = true) {
    executeWithExceptionHandling(ignoreExceptions) {
      if (SharedEvent::class.java.isAssignableFrom(EventType::class.java)) {
        SHARED_EVENTS_FLOW.unsubscribe(eventClass = EventType::class.java, subscriber = subscriber)
      }
      else
        EVENTS_FLOW.unsubscribe(eventClass = EventType::class.java, subscriber = subscriber)
    }
  }

  fun unsubscribeAll() {
    LOG.info("Unsubscribing all events")
    SHARED_EVENTS_FLOW.unsubscribeAll()
    EVENTS_FLOW.unsubscribeAll()
    LOG.info("Unsubscribed all events")
  }

  fun startServerProcess(ignoreExceptions: Boolean = true) {
    executeWithExceptionHandling(ignoreExceptions) {
      SHARED_EVENTS_FLOW.starterServerProcess()
    }
  }

  fun endServerProcess() {
    SHARED_EVENTS_FLOW.endServerProcess()
  }
}