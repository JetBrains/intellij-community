// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

internal sealed class PythonDebuggerScope : CoroutineScope, Disposable {
  override val coroutineContext: CoroutineContext =
    SupervisorJob() + CoroutineName(javaClass.name)

  override fun dispose() {
    cancel("Disposed ${javaClass.simpleName}")
  }

  companion object {
    /**
     * Schedules a coroutine to run on a given [CoroutineContext].
     */
    fun launchOn(context: CoroutineContext, action: suspend CoroutineScope.() -> Unit): Job =
      global.launch(context, block = action)


    /**
     * Retrieves global coroutine scope for the Python Debugger.
     * Designed to be used in places where the project scope is not available.
     */
    val global: PythonDebuggerScope get() = service<GlobalScopeService>()

    @Service
    private class GlobalScopeService : PythonDebuggerScope()

  }
}
