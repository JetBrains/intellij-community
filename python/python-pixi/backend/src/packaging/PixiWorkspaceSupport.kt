// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.python.pixi.backend.workspace.DEFAULT_ENVIRONMENT
import com.intellij.python.pixi.backend.workspace.findPixiWorkspace
import com.intellij.python.pixi.backend.workspace.parsePixiToml
import com.intellij.python.pixi.backend.workspace.PixiTomlTable
import com.intellij.python.pyproject.PyDependencyGroup
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.packaging.management.PythonWorkspaceSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.path.exists
import kotlin.io.path.readText

internal class PixiWorkspaceSupport(private val interpreter: PythonInterpreter) : PythonWorkspaceSupport {
  override suspend fun getWorkspaceMembers(projectName: ProjectName): List<PyWorkspaceMember> = listOf(PyWorkspaceMember(projectName.name))

  override suspend fun getDependencyGroups(projectName: ProjectName): Map<PyWorkspaceMember, List<PyDependencyGroup>> =
    withContext(Dispatchers.IO) {
      val workspace = findPixiWorkspace(interpreter.ownerDir) ?: return@withContext emptyMap()
      val manifest = workspace.root.resolve("pixi.toml").takeIf { it.exists() } ?: workspace.root.resolve("pyproject.toml")
      val parsed = parsePixiToml(manifest.readText()) ?: return@withContext emptyMap()
      val pixi = parsed.getTable("tool.pixi") ?: parsed
      val environment = interpreter.ref.envRef.value
      val definition = pixi.getTable("environments")?.get(listOf(environment))
      if (environment != DEFAULT_ENVIRONMENT && definition == null) return@withContext emptyMap()
      val table = definition as? PixiTomlTable
      val features = when (definition) {
        is List<*> -> definition.filterIsInstance<String>()
        is PixiTomlTable -> definition.getArray("features").orEmpty().filterIsInstance<String>()
        else -> emptyList()
      }
      val groups = listOfNotNull("default".takeUnless { table?.getBoolean("no-default-feature") == true }) + features
      mapOf(PyWorkspaceMember(projectName.name) to groups.distinct().map { PyDependencyGroup(it) })
    }
}
