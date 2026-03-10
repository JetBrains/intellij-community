// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@OptIn(ExperimentalCoroutinesApi::class)
fun <T : Any> Deferred<T>.getNow(): T? {
  return if (isCompleted && !isCancelled) getCompleted() else null
}

@ApiStatus.Internal
fun terminalProjectScope(project: Project): CoroutineScope {
  return project.service<TerminalProjectScopeProvider>().coroutineScope
}

internal fun terminalProjectScopeBoundToDisposable(project: Project, disposable: Disposable, name: String): CoroutineScope {
  val scope = terminalProjectScope(project).childScope(name)
  scope.coroutineContext.job.cancelOnDispose(disposable)
  return scope
}

@Service(Service.Level.PROJECT)
private class TerminalProjectScopeProvider(val coroutineScope: CoroutineScope)

internal fun terminalApplicationScope(): CoroutineScope {
  return service<TerminalApplicationScopeProvider>().coroutineScope
}

@Service(Service.Level.APP)
private class TerminalApplicationScopeProvider(val coroutineScope: CoroutineScope)
