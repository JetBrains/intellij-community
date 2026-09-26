// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
class MermaidPlugin(private val coroutineScope: CoroutineScope) {

  fun coroutineScope(): CoroutineScope {
    return coroutineScope
  }

  companion object {
    /**
     * Be aware, that you should not try to obtain the scope
     * when the plugin is unloading or the service is already disposed.
     *
     * Use `serviceOrNull<MermaidPlugin>()` in sensitive places.
     */
    fun coroutineScope(project: Project): CoroutineScope {
      return project.service<MermaidPlugin>().coroutineScope
    }
  }
}
