package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.tools.ide.starter.bus.events.Event

class TestContextInitializedEvent(val testContext: IDETestContext) : Event()