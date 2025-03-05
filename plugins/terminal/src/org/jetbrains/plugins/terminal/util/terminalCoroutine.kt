// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.util

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun terminalProjectScope(project: Project): CoroutineScope {
  return project.service<TerminalProjectScopeProvider>().coroutineScope
}

@Service(Service.Level.PROJECT)
private class TerminalProjectScopeProvider(val coroutineScope: CoroutineScope)

internal fun terminalApplicationScope(): CoroutineScope {
  return service<TerminalApplicationScopeProvider>().coroutineScope
}

@Service(Service.Level.APP)
private class TerminalApplicationScopeProvider(val coroutineScope: CoroutineScope)
