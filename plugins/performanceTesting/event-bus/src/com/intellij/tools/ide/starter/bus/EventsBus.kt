package com.intellij.tools.ide.starter.bus

import com.intellij.tools.ide.starter.bus.events.Event
import com.intellij.tools.ide.starter.bus.shared.SharedEventsFlow
import com.intellij.tools.ide.starter.bus.shared.client.LocalEventBusServerClient
import com.intellij.tools.ide.starter.bus.shared.events.SharedEvent
import com.intellij.tools.ide.starter.bus.shared.server.LocalEventBusServer
import com.intellij.tools.ide.starter.bus.local.LocalEventsFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

object EventsBus {
  val EVENTS_FLOW = LocalEventsFlow()
  val SHARED_EVENTS_FLOW = SharedEventsFlow(LocalEventBusServerClient(LocalEventBusServer), EVENTS_FLOW)

  /**
   *  Different events can be processed in parallel
   */
  fun <T : Event> postAndWaitProcessing(event: T) {
    if ((event is SharedEvent))
      SHARED_EVENTS_FLOW.postAndWaitProcessing(event)
    else
      EVENTS_FLOW.postAndWaitProcessing(event)
  }

  /** Can have only one subscription by pair subscriber + event
   * Subscriber might be invoked multiple times on different events since unsubscription happens only after end of test.
   *  */
  inline fun <reified EventType : Event> subscribe(
    subscriber: Any,
    timeout: Duration = 2.minutes,
    noinline callback: suspend (event: EventType) -> Unit
  ): EventsBus {
    if (SharedEvent::class.java.isAssignableFrom(EventType::class.java)) {
      SHARED_EVENTS_FLOW.subscribe(eventClass = EventType::class.java, subscriber = subscriber, timeout, callback)
      SHARED_EVENTS_FLOW.startServerPolling()
    }
    else
      EVENTS_FLOW.subscribe(eventClass = EventType::class.java, subscriber = subscriber, timeout, callback)
    return this
  }

  fun unsubscribeAll() {
    SHARED_EVENTS_FLOW.unsubscribeAll()
    EVENTS_FLOW.unsubscribeAll()
  }

  fun startServerProcess() {
    SHARED_EVENTS_FLOW.starterServerProcess()
  }

  fun endServerProcess() {
    SHARED_EVENTS_FLOW.endServerProcess()
  }
}