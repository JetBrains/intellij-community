// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda.environmentYml

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.FilesModificationTrackerBase
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserDataUnsafe
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.PythonPluginDisposable
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.conda.environmentYml.format.CondaEnvironmentYmlParser
import com.jetbrains.python.packaging.conda.environmentYml.format.EnvironmentYmlModifier
import com.jetbrains.python.packaging.dependencies.cache.PythonDependenciesManagerCached
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
class CondaEnvironmentYmlManager private constructor(project: Project, val sdk: Sdk) : PythonDependenciesManagerCached(project) {
  override fun getDependenciesFile(): VirtualFile? {
    return CondaEnvironmentYmlSdkUtils.findFile(sdk)
  }

  override fun addDependency(packageName: String): Boolean {
    val envFile = getDependenciesFile() ?: return false
    return EnvironmentYmlModifier.addRequirement(project, envFile, packageName)
  }

  override fun getModificationTracker(): FilesModificationTrackerBase {
    return CondaEnvYamlModificationTracker.getInstance(project)
  }

  override fun parseRequirements(requirementsFile: VirtualFile): List<PyRequirement>? {
    return CondaEnvironmentYmlParser.fromFile(requirementsFile)
  }

  companion object {
    private val KEY = Key<CondaEnvironmentYmlManager>(this::class.java.name)

    fun getInstance(project: Project, sdk: Sdk): CondaEnvironmentYmlManager = sdk.getOrCreateUserDataUnsafe(KEY) {
      CondaEnvironmentYmlManager(project, sdk).also {
        Disposer.register(PythonPluginDisposable.getInstance(project), it)
        Disposer.register(it, Disposable { sdk.putUserData(KEY, null) })
      }
    }
  }
}