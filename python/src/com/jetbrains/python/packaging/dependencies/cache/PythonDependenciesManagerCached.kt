// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.dependencies.cache

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.FilesModificationTrackerBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.dependencies.PythonDependenciesManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class PythonDependenciesManagerCached(val project: Project) : PythonDependenciesManager {
  private val cachedEnvRequirements = CachedValuesManager.getManager(project).createCachedValue {
    val envFile = getDependenciesFile()
    val requirements = try {
      envFile?.let { parseRequirements(envFile) }
    }
    catch (t: Throwable) {
      thisLogger().warn("Failed to parse $envFile", t)
      null
    }
    CachedValueProvider.Result.create(requirements,
                                      ProjectRootManager.getInstance(project),
                                      getModificationTracker())
  }

  final override fun getDependencies(): List<PyRequirement>? = cachedEnvRequirements.value

  protected abstract fun getModificationTracker(): FilesModificationTrackerBase
  protected abstract fun parseRequirements(requirementsFile: VirtualFile): List<PyRequirement>?
}