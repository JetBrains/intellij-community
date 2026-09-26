// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.jetbrains.python.debugger.PythonDebuggerScope.Companion.childScope
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Provides coroutine scopes for Python debugger operations at both application and project levels.
 * 
 * Use [childScope] with or without a project parameter for application-level or project-specific operations.
 */
@Internal
@Service(Service.Level.APP, Service.Level.PROJECT)
class PythonDebuggerScope(private val coroutineScope: CoroutineScope) {
  companion object {
    /**
     * Creates a child coroutine scope for Python debugger operations.
     * The child scope prevents cancellation of the parent scope.
     * 
     * @param project Optional project for project-level scope. If null, uses application-level scope.
     * @param name Debug name for the child scope
     */
    @JvmStatic
    @JvmOverloads
    fun childScope(project: Project? = null, name: String): CoroutineScope {
      return project?.service<PythonDebuggerScope>()?.coroutineScope?.childScope(name)
             ?: service<PythonDebuggerScope>().coroutineScope.childScope(name)
    }
  }
}
