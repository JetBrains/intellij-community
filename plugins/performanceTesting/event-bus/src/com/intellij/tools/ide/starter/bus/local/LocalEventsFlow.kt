package com.intellij.tools.ide.starter.bus.local

import com.intellij.tools.ide.starter.bus.EventsFlow
import com.intellij.tools.ide.starter.bus.Subscriber
import com.intellij.tools.ide.starter.bus.events.Event
import com.intellij.tools.ide.starter.bus.logger.EventBusLoggerFactory
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList
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
    val exceptions = CopyOnWriteArrayList<Throwable>()
    (subscribersForEvent as? CopyOnWriteArrayList<Subscriber<T>>)
      ?.map { subscriber ->
        // In case the job is interrupted (e.g. due to timeout), the coroutine may enter Cancelling state
        // and finish before the 'catch' block is executed. Using CompletableDeferred ensures we wait
        // until either successful completion or proper exception handling has occurred.
        val result = CompletableDeferred<Unit>()
        LOG.debug("Post event $eventClassName for $subscriber.")
        // Launching a new coroutine for each subscriber
        scope.launch {
          LOG.debug("Start execution $eventClassName for $subscriber")
          // Blocking the current coroutine to enforce timeout and interruptibility
          runBlocking {
            // Enforces a timeout for the entire subscriber execution
            withTimeout(subscriber.timeout) {
              // Ensures the operation inside is interruptible — if the thread is blocked,
              // it will be interrupted when the coroutine is cancelled (e.g. by timeout)
              runInterruptible {
                runBlocking {
                  // Switch to IO dispatcher for potentially blocking I/O operations and for more workers
                  withContext(Dispatchers.IO) {
                    try {
                      subscriber.callback(event)
                      result.complete(Unit)
                    }
                    catch (e: Throwable) {
                      exceptions.add(e)
                      result.complete(Unit)
                    }
                  }
                }
              }
            }
          }
          LOG.debug("Finished execution $eventClassName for $subscriber")
        }
        return@map result
      }
      ?.forEach {
        try {
          runBlocking { it.await() }
        }
        catch (e: Throwable) {
          LOG.info("Exception occurred while processing $e")
          exceptions.add(e)
        }
      }

    LOG.debug("All exceptions: $exceptions")
    if (exceptions.isNotEmpty()) {
      val exceptionsString = exceptions.joinToString(separator = "\n") { e -> "${exceptions.indexOf(e) + 1}) ${e.message}" }
      throw IllegalArgumentException("Exceptions occurred while processing subscribers. $exceptionsString")
    }
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