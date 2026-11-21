// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.module.Module
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

/** Hears that the Python project on a module got another interpreter. It fires on the application bus. */
@ApiStatus.Internal
interface PythonInterpreterListener {
  /** [module] had [previous] and has [current] now. Either is `null` when the module had or has no interpreter. */
  fun moduleInterpreterChanged(module: Module, previous: PythonInterpreter?, current: PythonInterpreter?)

  companion object {
    val TOPIC: Topic<PythonInterpreterListener> = Topic.create("Python interpreter listener", PythonInterpreterListener::class.java)
  }
}
