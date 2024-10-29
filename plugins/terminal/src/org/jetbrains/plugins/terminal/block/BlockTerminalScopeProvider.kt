// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Service(Service.Level.PROJECT)
internal class BlockTerminalScopeProvider(private val coroutineScope: CoroutineScope) {
  fun childScope(name: String, context: CoroutineContext = EmptyCoroutineContext, supervisor: Boolean = true): CoroutineScope {
    return coroutineScope.childScope(name, context, supervisor)
  }

  companion object {
    fun getInstance(project: Project): BlockTerminalScopeProvider = project.service()
  }
}
