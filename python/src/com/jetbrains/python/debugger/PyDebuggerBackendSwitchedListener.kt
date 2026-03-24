// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PyDebuggerBackendSwitchedListener {
  fun backendSwitched(project: Project, from: PyDebuggerBackend, to: PyDebuggerBackend)

  companion object {
    @JvmField
    val TOPIC: Topic<PyDebuggerBackendSwitchedListener> =
      Topic.create("python.debugger.backend.switched", PyDebuggerBackendSwitchedListener::class.java)
  }
}
