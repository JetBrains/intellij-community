package com.intellij.tools.ide.starter.bus.local

import com.intellij.tools.ide.starter.bus.EventsFlow
import com.intellij.tools.ide.starter.bus.Subscriber
import com.intellij.tools.ide.starter.bus.events.Event
import com.intellij.tools.ide.starter.bus.exceptions.EventBusException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock
import kotlin.jvm.internal.CallableReference
import kotlin.time.Duration

class LocalEventsFlow : EventsFlow {
  // There is lock only on subscribers(HashMap).Subscriber list(subscribers.value)
  // can be changed from different threads, so it should be thread safe,
  // but iteration by one event (subscriber.key) should not block iteration by another event.
  // Therefore, CopyOnWriteArrayList is using
  private val subscribers = HashMap<String, CopyOnWriteArrayList<Subscriber<out Event>>>()
  private val subscribersLock = ReentrantReadWriteLock()

  override fun <EventType : Event> subscribe(eventClass: Class<EventType>,
                                             subscriber: Any,
                                             timeout: Duration,
                                             callback: suspend (event: EventType) -> Unit): Boolean {
    subscribersLock.writeLock().withLock {
      // simpleName because of in the case of SharedEvent, events can be located in different packages
      val eventClassName = eventClass.simpleName
      // In case using class as subscriber. eg MyClass::class
      val subscriberObject = if (subscriber is CallableReference) subscriber::class.toString() else subscriber
      // To avoid double subscriptions
      if (subscribers[eventClassName]?.any { it.subscriberName == subscriberObject } == true) return false
      val newSubscriber = Subscriber(subscriberObject, timeout, callback)
      println("New subscriber $newSubscriber for $eventClassName")
      subscribers.computeIfAbsent(eventClassName) { CopyOnWriteArrayList() }.add(newSubscriber)
      return true
    }
  }

  override fun <T : Event> postAndWaitProcessing(event: T) {
    val eventClassName = event.javaClass.simpleName
    val subscribersForEvent = subscribersLock.readLock().withLock {
      subscribers[eventClassName]
    }
    (subscribersForEvent as? CopyOnWriteArrayList<Subscriber<T>>)
      ?.map { subscriber ->
        println("Post event $eventClassName for $subscriber.")
        CompletableFuture.runAsync({
                                     println("Start execution $eventClassName for $subscriber")
                                     runBlocking { subscriber.callback(event) }
                                     println("Finished execution $eventClassName for $subscriber")
                                   }, Dispatchers.IO.asExecutor())
          .orTimeout(subscriber.timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
          .exceptionally { throwable ->
            throw EventBusException(eventClassName, subscriber.subscriberName.toString(),
                                    subscribersForEvent.map { it.subscriberName },
                                    throwable)
          }
      }
      ?.forEach {
        try {
          it.join()
        }
        catch (e: CompletionException) {
          throw e.cause ?: e
        }
      }
  }

  override fun unsubscribeAll() {
    subscribersLock.writeLock().withLock {
      subscribers.clear()
    }
  }
}