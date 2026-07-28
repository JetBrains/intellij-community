// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.McpProjectDependenciesProvider
import com.intellij.mcpserver.McpProjectDependency
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator

internal class JpsDependenciesProvider : McpProjectDependenciesProvider {
  override suspend fun collectDependencies(project: Project) = readAction {
    val moduleManager = ModuleManager.getInstance(project)
    moduleManager.modules.flatMap { module ->
      OrderEnumerator.orderEntries(module).librariesOnly().classes().roots.map { root ->
        McpProjectDependency(name = root.name, source = "jps-library")
      }
    }.distinctBy { it.name }
  }
}
