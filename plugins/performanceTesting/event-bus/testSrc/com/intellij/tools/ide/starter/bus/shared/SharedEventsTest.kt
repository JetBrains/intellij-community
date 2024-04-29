package com.intellij.tools.ide.starter.bus.shared

import com.intellij.tools.ide.starter.bus.EventsBus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach

abstract class SharedEventsTest {

  @BeforeEach
  fun abstractBeforeEach() {
    try {
      EventsBus.startServerProcess()
    }
    catch (t: Throwable) {
      Assumptions.abort("Can't start event bus server")
    }
  }

  @AfterEach
  fun abstractAfterEach() {
    EventsBus.unsubscribeAll()
    EventsBus.endServerProcess()
  }
}