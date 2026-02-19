package com.intellij.ide.starter.junit5

import com.intellij.tools.ide.starter.bus.EventsBus
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

class StarterBusAllTestCallback : AfterAllCallback {
  override fun afterAll(context: ExtensionContext?) {
    EventsBus.unsubscribeAll()
  }
}