package com.intellij.tools.ide.starter.bus.shared.server.services

import com.intellij.tools.ide.starter.bus.logger.EventBusLoggerFactory
import com.intellij.tools.ide.starter.bus.shared.dto.SharedEventDto
import com.intellij.tools.ide.starter.bus.shared.dto.SubscriberDto
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

private val LOG = EventBusLoggerFactory.getLogger(EventsFlowService::class.java)

class EventsFlowService {
  // Key is processId. Necessary to avoid sending old events for processes that signed up later
  private val eventsPerProcess = HashMap<String, HashMap<String, EventsWithTimeout>>()
  private val eventsPerProcessLock = ReentrantReadWriteLock()

  private val eventsLatch = HashMap<String, CountDownLatch>()
  private val eventsLatchLock = ReentrantReadWriteLock()

  private val lockByEvent = HashMap<String, Any>()

  private fun getLock(key: String): Any {
    synchronized(lockByEvent) {
      return lockByEvent.computeIfAbsent(key) { _ -> Any() }
    }
  }

  fun postAndWaitProcessing(sharedEventDto: SharedEventDto) {
    LOG.debug("Before synchronized")
    synchronized(getLock(sharedEventDto.eventId)) {
      LOG.debug("Start synchronized")
      var timeout: Long
      val latch = CountDownLatch(eventsPerProcessLock.readLock().withLock {
        val subscribers = eventsPerProcess.values.filter { it[sharedEventDto.eventName] != null }
        // Sum of all subscriber timeouts
        timeout = subscribers.flatMap { subscribersPerProcess -> subscribersPerProcess.values.map { it.timeoutMs } }.sum()
        subscribers.size
      })
      eventsLatchLock.writeLock().withLock { eventsLatch[sharedEventDto.eventId] = latch }
      eventsPerProcessLock.writeLock().withLock {
        eventsPerProcess.values.forEach { it[sharedEventDto.eventName]?.events?.add(sharedEventDto) }
      }
      LOG.debug("Before latch awaiting. Count ${latch.count}")
      latch.await(timeout, TimeUnit.MILLISECONDS)
      LOG.debug("After latch awaiting")
    }
  }

  fun newSubscriber(subscriber: SubscriberDto) {
    synchronized(subscriber.eventName) {
      eventsPerProcessLock.writeLock().withLock {
        eventsPerProcess
          .computeIfAbsent(subscriber.processId) { HashMap() }
          .computeIfAbsent(subscriber.eventName) { EventsWithTimeout(subscriber.timeoutMs, mutableListOf()) }
      }
    }
    LOG.info("New subscriber $subscriber")
  }

  fun getEvents(processId: String): Map<String, MutableList<SharedEventDto>> {
    return eventsPerProcessLock.readLock().withLock {
      eventsPerProcess.getOrDefault(processId, HashMap()).map { it.key to it.value.events }.toMap()
    }
  }

  fun processedEvent(eventId: String) {
    eventsLatchLock.readLock().withLock {
      eventsLatch[eventId]?.countDown()
    }
  }

  fun clear() {
    eventsLatchLock.writeLock().withLock {
      eventsLatch.clear()
    }
    eventsPerProcessLock.writeLock().withLock {
      eventsPerProcess.clear()
    }
    lockByEvent.clear()
  }

  private data class EventsWithTimeout(val timeoutMs: Long, val events: MutableList<SharedEventDto>)
}