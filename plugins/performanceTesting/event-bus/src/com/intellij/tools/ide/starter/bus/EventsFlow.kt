package com.intellij.tools.ide.starter.bus

import com.intellij.tools.ide.starter.bus.events.Event
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface EventsFlow {
  fun unsubscribeAll()
  fun <EventType : Event> subscribe(eventClass: Class<EventType>,
                                                          subscriber: Any,
                                                          timeout: Duration = 30.seconds,
                                                          callback: suspend (event: EventType) -> Unit): Boolean

  fun <T : Event> postAndWaitProcessing(event: T)
  fun <EventType : Event> unsubscribe(eventClass: Class<EventType>, subscriber: Any)
  fun getSubscriberObject(subscriber: Any): Any
}