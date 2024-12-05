// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.util

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal fun terminalProjectScope(
  project: Project,
  name: String,
  context: CoroutineContext = EmptyCoroutineContext,
  supervisor: Boolean = true,
): CoroutineScope {
  return project.service<TerminalProjectScopeProvider>().coroutineScope.childScope(name, context, supervisor)
}

@Service(Service.Level.PROJECT)
private class TerminalProjectScopeProvider(val coroutineScope: CoroutineScope)

internal fun terminalApplicationScope(
  name: String,
  context: CoroutineContext = EmptyCoroutineContext,
  supervisor: Boolean = true,
): CoroutineScope {
  return service<TerminalApplicationScopeProvider>().coroutineScope.childScope(name, context, supervisor)
}

@Service(Service.Level.APP)
private class TerminalApplicationScopeProvider(val coroutineScope: CoroutineScope)
