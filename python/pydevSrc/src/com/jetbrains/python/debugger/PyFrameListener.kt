// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.Topic
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.impl.frame.XStandaloneVariablesView

interface PyFrameListener {
  fun frameChanged()
  fun sessionStopped(communication: PyFrameAccessor?) {}
  fun valuesUpdated(communication: PyFrameAccessor, values: XValueChildrenList) {}
  fun viewCreated(communication: PyFrameAccessor, view: XStandaloneVariablesView) {}

  companion object {
    @JvmStatic
    fun publisher(): PyFrameListener {
      return ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC)
    }

    @Topic.AppLevel
    val TOPIC = Topic.create("PyFrameListener", PyFrameListener::class.java)
  }
}