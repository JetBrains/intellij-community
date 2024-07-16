package com.intellij.tools.ide.starter.bus.shared.client

import com.intellij.tools.ide.starter.bus.events.Event
import com.intellij.tools.ide.starter.bus.shared.dto.SharedEventDto

interface EventBusServerClient {
  fun postAndWaitProcessing(sharedEventDto: SharedEventDto): Boolean
  fun newSubscriber(eventClass: Class<out Event>)
  fun getEvents(): Map<String, List<Pair<String, Event>>?>
  fun processedEvent(eventName: String)
  fun endServerProcess()
  fun startServerProcess()
  fun clear()
}