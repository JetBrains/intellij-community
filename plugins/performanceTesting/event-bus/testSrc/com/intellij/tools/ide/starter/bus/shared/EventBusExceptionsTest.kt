package com.intellij.tools.ide.starter.bus.shared

import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.starter.bus.shared.events.SharedEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EventBusExceptionsTest {
  @AfterEach
  fun tearDown() {
    EventsBus.unsubscribeAll()
  }

  @Test
  fun `Subscribe - still execute if ignore exceptions and server is nor running `() {
    EventsBus.subscribe("Subscriber") { _: SharedEvent -> }
    assertTrue(true)
  }

  @Test
  fun `Subscribe - get error if don't ignore exceptions and server is nor running `() {
    var result = false
    try {
      EventsBus.subscribe("Subscriber", ignoreExceptions = false) { _: SharedEvent -> }
    }
    catch (t: Throwable) {
      result = true
    }
    assertTrue(result)
  }

  @Test
  fun `Post and wait - still execute if ignore exceptions and server is nor running `() {
    EventsBus.postAndWaitProcessing(SharedEvent())
    assertTrue(true)
  }

  @Test
  fun `Post and wait - get error if don't ignore exceptions and server is nor running `() {
    var result = false
    try {
      EventsBus.postAndWaitProcessing(SharedEvent(), ignoreExceptions = false)
    }
    catch (t: Throwable) {
      result = true
    }
    assertTrue(result)
  }
}