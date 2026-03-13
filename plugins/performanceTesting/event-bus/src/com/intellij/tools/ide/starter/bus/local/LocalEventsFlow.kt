// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.bus.local

import com.intellij.tools.ide.starter.bus.EventsFlow
import com.intellij.tools.ide.starter.bus.Subscriber
import com.intellij.tools.ide.starter.bus.events.Event
import com.intellij.tools.ide.starter.bus.logger.EventBusLoggerFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
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
  private var parentJob = Job()
  private var scope = CoroutineScope(Dispatchers.IO) + parentJob
  private companion object {
    private val eventIdCounter = AtomicLong(0)
  }

  // In case using class as subscriber. eg MyClass::class
  override fun getSubscriberObject(subscriber: Any) = if (subscriber is CallableReference) subscriber::class.toString() else subscriber

  override fun <EventType : Event> unsubscribe(eventClass: Class<EventType>, subscriber: Any) {
    subscribersLock.writeLock().withLock {
      unsubscribeNoLock(eventClass, subscriber)
    }
  }

  private fun <EventType : Event> unsubscribeNoLock(eventClass: Class<EventType>, subscriber: Any) {
    val eventClassName = eventClass.simpleName
    val subscriberName = getSubscriberObject(subscriber)
    subscribers[eventClassName]?.removeIf { it.subscriberName == subscriberName }
    LOG.info("Unsubscribing $subscriberName for $eventClassName")
  }

  private fun <EventType : Event> subscribe(
    eventClass: Class<EventType>,
    subscriber: Any,
    executeOnce: Boolean,
    timeout: Duration,
    sequential: Boolean,
    callback: suspend (event: EventType) -> Unit,
  ): Boolean {
    subscribersLock.writeLock().withLock {
      // simpleName because of in the case of SharedEvent, events can be located in different packages
      val eventClassName = eventClass.simpleName
      val subscriberObject = getSubscriberObject(subscriber)
      // To avoid double subscriptions
      if (subscribers[eventClassName]?.any { it.subscriberName == subscriberObject } == true) {
        LOG.info("Subscriber $subscriberObject is already subscribed for $eventClassName")
        return false
      }
      val newSubscriber = Subscriber(subscriberObject, timeout, executeOnce = executeOnce, sequential = sequential, callback)
      LOG.info("New subscriber $newSubscriber for $eventClassName")
      subscribers.computeIfAbsent(eventClassName) { CopyOnWriteArrayList() }.add(newSubscriber)
      return true
    }
  }

  override fun <EventType : Event> subscribeOnce(
    eventClass: Class<EventType>,
    subscriber: Any,
    timeout: Duration,
    sequential: Boolean,
    callback: suspend (event: EventType) -> Unit,
  ): Boolean = subscribe(eventClass, subscriber, executeOnce = true, timeout, sequential = sequential, callback)

  override fun <EventType : Event> subscribe(
    eventClass: Class<EventType>,
    subscriber: Any,
    timeout: Duration,
    sequential: Boolean,
    callback: suspend (event: EventType) -> Unit,
  ): Boolean = subscribe(eventClass, subscriber, executeOnce = false, timeout, sequential = sequential, callback)

  override fun <T : Event> postAndWaitProcessing(event: T) {
    val eventId = eventIdCounter.incrementAndGet()
    val eventClassName = event.javaClass.simpleName
    val subscribersForEvent = subscribersLock.writeLock().withLock {
      subscribers[eventClassName]?.toList().also { allSubscribersForEvent ->
        allSubscribersForEvent?.forEach { subscriber ->
          if (subscriber.executeOnce) {
            unsubscribeNoLock(event.javaClass, subscriber.subscriberName)
          }
        }
      }
    }
    if (subscribersForEvent.isNullOrEmpty()) {
      return
    }

    val (blockingSubscribers, nonblockingSubscribers) = subscribersForEvent.partition { it.sequential }
    LOG.info("[$eventId] PostAndWait start for $eventClassName: total=${subscribersForEvent.size}, sequential=${blockingSubscribers.size}, parallel=${nonblockingSubscribers.size}")
    val exceptions = processSequentially(eventId, blockingSubscribers, eventClassName, event) +
                     processInParallel(eventId, nonblockingSubscribers, eventClassName, event)

    LOG.info("[$eventId] PostAndWait finished for $eventClassName. Exceptions: $exceptions")
    if (exceptions.isNotEmpty()) {
      val exceptionsString = exceptions.joinToString(separator = "\n") { e -> "${exceptions.indexOf(e) + 1}) ${e.message}" }
      throw IllegalArgumentException("[$eventId] Exceptions occurred while processing subscribers. $exceptionsString")
    }
  }

  private fun <T : Event> processInParallel(
    eventId: Long,
    subscribersForEvent: List<Subscriber<out Event>>,
    eventClassName: String,
    event: T,
  ): CopyOnWriteArrayList<Throwable> {
    val exceptions = CopyOnWriteArrayList<Throwable>()
    val tasks = (subscribersForEvent as List<Subscriber<T>>).map { subscriber ->
      // In case the job is interrupted (e.g. due to timeout), the coroutine may enter Cancelling state
      // and finish before the 'catch' block is executed. Using CompletableDeferred ensures we wait
      // until either successful completion or proper exception handling has occurred.
      val result = CompletableDeferred<Unit>()
      LOG.debug("[$eventId] Post event $eventClassName for $subscriber")
      // Launching a new coroutine for each subscriber
      scope.launch(Dispatchers.IO + CoroutineName("[$eventId] Processing $eventClassName for $subscriber")) {
        LOG.debug("[$eventId] Start execution $eventClassName for $subscriber")
        // Enforces a timeout for the entire subscriber execution
        withTimeout(subscriber.timeout) {
          // Ensures the operation inside is interruptible — if the thread is blocked,
          // it will be interrupted when the coroutine is cancelled (e.g. by timeout)
          runInterruptible {
            try {
              runBlocking(CoroutineName("[$eventId] Processing $eventClassName for $subscriber")) {
                subscriber.callback(event)
              }
              result.complete(Unit)
            }
            catch (e: Throwable) {
              exceptions.add(e)
              result.complete(Unit)
            }
          }
        }
        LOG.debug("[$eventId] Finished execution $eventClassName for $subscriber")
      }
      return@map result to subscriber
    }

    runBlocking(CoroutineName("[$eventId] Awaiting for subscribers $subscribersForEvent")) {
      // awaitAll shouldn't be used as it would stop after the first exception from any coroutine
      tasks.forEach { (task, subscriber) ->
        try {
          task.await()
        }
        catch (e: Throwable) {
          LOG.info("[$eventId] Exception occurred while processing $e for $subscriber")
          exceptions.add(e)
        }
      }
    }
    return exceptions
  }

  private fun <T : Event> processSequentially(
    eventId: Long,
    subscribersForEvent: List<Subscriber<out Event>>,
    eventClassName: String,
    event: T,
  ): CopyOnWriteArrayList<Throwable> {
    val exceptions = CopyOnWriteArrayList<Throwable>()
    // Execute strictly one-by-one in the current thread (wrapped with runBlocking for suspend compatibility)
    runBlocking(CoroutineName("[$eventId] Processing sequentially $eventClassName for ${subscribersForEvent.size} subscribers")) {
      @Suppress("UNCHECKED_CAST")
      val typed = subscribersForEvent as List<Subscriber<T>>
      for (subscriber in typed) {
        LOG.debug("[$eventId] Start sequential execution $eventClassName for $subscriber")
        try {
          withTimeout(subscriber.timeout) {
            // If the code inside blocks a thread, make it interruptible for cooperative cancellation
            runInterruptible {
              // We need a blocking bridge to call suspend callback from interruptible block
              runBlocking(CoroutineName("[$eventId] Processing $eventClassName for $subscriber sequentially")) {
                subscriber.callback(event)
              }
            }
          }
        }
        catch (e: Throwable) {
          LOG.info("[$eventId] Exception occurred while sequential processing $eventClassName for $subscriber: $e")
          exceptions.add(e)
        }
        finally {
          LOG.debug("[$eventId] Finished sequential execution $eventClassName for $subscriber")
        }
      }
    }
    return exceptions
  }

  override fun unsubscribeAll() {
    subscribersLock.writeLock().withLock {
      subscribers.clear()
    }
    try {
      runBlocking { parentJob.cancelAndJoin() }
    }
    catch (t: Throwable) {
      LOG.info("Scope was not canceled, $t")
    }
    finally {
      parentJob = Job()
      scope = CoroutineScope(Dispatchers.IO) + parentJob
    }
  }
}