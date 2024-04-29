package com.intellij.tools.ide.starter.bus.shared.server.services

import com.intellij.tools.ide.starter.bus.shared.dto.SharedEventDto
import com.intellij.tools.ide.starter.bus.shared.dto.SubscriberDto
import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

class EventsFlowService {
  // Key is processId. Necessary to avoid sending old events for processes that signed up later
  private val eventsPerProcess = HashMap<String, HashMap<String, MutableList<SharedEventDto>>>()
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
    synchronized(getLock(sharedEventDto.eventId)) {
      val latch = CountDownLatch(eventsPerProcessLock.readLock().withLock {
        eventsPerProcess.values.filter {it[sharedEventDto.eventName] != null }.size
      })
      eventsLatchLock.writeLock().withLock { eventsLatch[sharedEventDto.eventId] = latch }
      eventsPerProcessLock.writeLock().withLock {
        eventsPerProcess.values.forEach { it[sharedEventDto.eventName]?.add(sharedEventDto) }
      }
      latch.await()
    }
  }

  fun newSubscriber(subscriber: SubscriberDto) {
    synchronized(subscriber.eventName) {
      eventsPerProcessLock.writeLock().withLock {
        eventsPerProcess
          .computeIfAbsent(subscriber.processId) { HashMap() }
          .computeIfAbsent(subscriber.eventName) { mutableListOf() }
      }
    }
  }

  fun getEvents(processId: String): HashMap<String, MutableList<SharedEventDto>> {
    return eventsPerProcessLock.readLock().withLock { eventsPerProcess.getOrDefault(processId, HashMap()) }
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
}