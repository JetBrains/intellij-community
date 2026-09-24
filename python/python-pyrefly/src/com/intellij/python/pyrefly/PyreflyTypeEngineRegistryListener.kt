// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyrefly

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineProvider
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
private class PyreflyTypeEngineRegistryListenerService(project: Project, coroutineScope: CoroutineScope) {
  init {
    Registry.get("pyrefly.type.engine").addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        PyTypeEngineProvider.updateLspServers(project)
      }
    }, coroutineScope)
  }
}

internal class PyreflyTypeEngineRegistryListener : ProjectActivity {
  override suspend fun execute(project: Project) {
    project.service<PyreflyTypeEngineRegistryListenerService>()
  }
}
