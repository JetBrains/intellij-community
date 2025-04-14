// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  // Key is processId. Necessary to avoid sending old events for processes that signed up later.
  // The second key is the event name
  private val subscribersPerProcess = HashMap<String, HashMap<String, SubscribersWithEvents>>()
  private val subscribersPerProcessLock = ReentrantReadWriteLock()

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
      val latch = CountDownLatch(subscribersPerProcessLock.readLock().withLock {
        // One process with many subscribers == one subscriber
        val matchingSubscribers = subscribersPerProcess.values.filter { it[sharedEventDto.eventName] != null }
        // Sum of all timeouts of all subscribers for all processes
        timeout = matchingSubscribers
          .flatMap { it.values }
          .flatMap { it.subscribers }
          .sumOf { it.timeoutMs }
        matchingSubscribers.size
      })
      eventsLatchLock.writeLock().withLock { eventsLatch[sharedEventDto.eventId] = latch }
      subscribersPerProcessLock.writeLock().withLock {
        subscribersPerProcess.values.forEach { it[sharedEventDto.eventName]?.events?.add(sharedEventDto) }
      }
      LOG.debug("Before latch awaiting. Count ${latch.count}")
      latch.await(timeout, TimeUnit.MILLISECONDS)
      LOG.debug("After latch awaiting")
    }
  }

  fun newSubscriber(subscriber: SubscriberDto) {
    synchronized(subscriber.eventName) {
      subscribersPerProcessLock.writeLock().withLock {
        subscribersPerProcess
          .computeIfAbsent(subscriber.processId) { HashMap() }
          .computeIfAbsent(subscriber.eventName) { SubscribersWithEvents(mutableListOf(), mutableListOf()) }
          .subscribers.add(subscriber)
      }
    }
    LOG.info("New subscriber $subscriber")
  }

  fun unsubscribe(subscriber: SubscriberDto) {
    synchronized(subscriber.eventName) {
      subscribersPerProcessLock.writeLock().withLock {
        val data = subscribersPerProcess[subscriber.processId]
        data?.get(subscriber.eventName)?.subscribers?.removeIf { it.subscriberName == subscriber.subscriberName }
      }
    }
    LOG.debug("Unsubscribed $subscriber")
  }

  fun getEvents(processId: String): Map<String, MutableList<SharedEventDto>> {
    return subscribersPerProcessLock.readLock().withLock {
      subscribersPerProcess.getOrDefault(processId, HashMap()).entries.associate { it.key to it.value.events }
    }
  }

  /**
   * Called after all subscribers in the process have processed the event
   */
  fun processedEvent(eventId: String) {
    eventsLatchLock.readLock().withLock {
      eventsLatch[eventId]?.countDown()
    }
  }

  fun clear() {
    eventsLatchLock.writeLock().withLock {
      eventsLatch.clear()
    }
    subscribersPerProcessLock.writeLock().withLock {
      subscribersPerProcess.clear()
    }
    lockByEvent.clear()
  }

  private data class SubscribersWithEvents(
    val subscribers: MutableList<SubscriberDto>,
    val events: MutableList<SharedEventDto>,
  )
}