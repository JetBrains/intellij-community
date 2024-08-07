package com.intellij.tools.ide.starter.bus.local

import com.intellij.tools.ide.starter.bus.EventsFlow
import com.intellij.tools.ide.starter.bus.Subscriber
import com.intellij.tools.ide.starter.bus.events.Event
import com.intellij.tools.ide.starter.bus.exceptions.EventBusException
import com.intellij.tools.ide.starter.bus.logger.EventBusLoggerFactory
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

private val LOG = EventBusLoggerFactory.getLogger(LocalEventsFlow::class.java)

class LocalEventsFlow : EventsFlow {
  // There is lock only on subscribers(HashMap).Subscriber list(subscribers.value)
  // can be changed from different threads, so it should be thread safe,
  // but iteration by one event (subscriber.key) should not block iteration by another event.
  // Therefore, CopyOnWriteArrayList is using
  private val subscribers = HashMap<String, CopyOnWriteArrayList<Subscriber<out Event>>>()
  private val subscribersLock = ReentrantReadWriteLock()

  // In case using class as subscriber. eg MyClass::class
  override fun getSubscriberObject(subscriber: Any) = if (subscriber is CallableReference) subscriber::class.toString() else subscriber

  override fun <EventType : Event> unsubscribe(eventClass: Class<EventType>, subscriber: Any) {
    subscribersLock.writeLock().withLock {
      val eventClassName = eventClass.simpleName
      val subscriberName = getSubscriberObject(subscriber)
      subscribers[eventClassName]?.removeIf { it.subscriberName == subscriberName }
      LOG.debug("Unsubscribing $subscriberName for $eventClassName")
    }
  }

  override fun <EventType : Event> subscribe(
    eventClass: Class<EventType>,
    subscriber: Any,
    timeout: Duration,
    callback: suspend (event: EventType) -> Unit,
  ): Boolean {
    subscribersLock.writeLock().withLock {
      // simpleName because of in the case of SharedEvent, events can be located in different packages
      val eventClassName = eventClass.simpleName
      val subscriberObject = getSubscriberObject(subscriber)
      // To avoid double subscriptions
      if (subscribers[eventClassName]?.any { it.subscriberName == subscriberObject } == true) return false
      val newSubscriber = Subscriber(subscriberObject, timeout, callback)
      LOG.debug("New subscriber $newSubscriber for $eventClassName")
      subscribers.computeIfAbsent(eventClassName) { CopyOnWriteArrayList() }.add(newSubscriber)
      return true
    }
  }

  override fun <T : Event> postAndWaitProcessing(event: T) {
    val eventClassName = event.javaClass.simpleName
    val subscribersForEvent = subscribersLock.readLock().withLock {
      subscribers[eventClassName]
    }
    val exceptions = mutableListOf<Throwable>()
    (subscribersForEvent as? CopyOnWriteArrayList<Subscriber<T>>)
      ?.map { subscriber ->
        LOG.debug("Post event $eventClassName for $subscriber.")
        CompletableFuture.runAsync({
                                     LOG.debug("Start execution $eventClassName for $subscriber")
                                     runBlocking { subscriber.callback(event) }
                                     LOG.debug("Finished execution $eventClassName for $subscriber")
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
          exceptions.add(e)
        }
      }

    if (exceptions.isNotEmpty()) {
      val exceptionsString = exceptions.joinToString(separator = "\n") { e -> "${exceptions.indexOf(e) + 1}) ${e.message}" }
      throw IllegalArgumentException("Exceptions occurred while processing subscribers. $exceptionsString")
    }
  }

  override fun unsubscribeAll() {
    subscribersLock.writeLock().withLock {
      subscribers.clear()
    }
  }
}