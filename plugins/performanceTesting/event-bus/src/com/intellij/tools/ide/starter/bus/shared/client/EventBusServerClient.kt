// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.bus.shared.client

import com.intellij.tools.ide.starter.bus.events.Event
import com.intellij.tools.ide.starter.bus.shared.dto.SharedEventDto
import kotlin.time.Duration

interface EventBusServerClient {
  fun postAndWaitProcessing(sharedEventDto: SharedEventDto): Boolean
  fun newSubscriber(eventClass: Class<out Event>, timeout: Duration, subscriberName: String)
  fun unsubscribe(eventClass: Class<out Event>, subscriberName: String)
  fun getEvents(): Map<String, List<Pair<String, Event>>?>
  fun processedEvent(eventName: String)
  fun endServerProcess()
  fun startServerProcess()
  fun clear()
}