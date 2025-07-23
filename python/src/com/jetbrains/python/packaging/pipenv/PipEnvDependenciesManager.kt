// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pipenv

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
import com.jetbrains.python.packaging.dependencies.cache.PythonDependenciesManagerCached
import com.jetbrains.python.sdk.pipenv.getPipFileLock
import com.jetbrains.python.sdk.pipenv.getPipFileLockRequirements

internal class PipEnvDependenciesManager private constructor(project: Project, val sdk: Sdk) : PythonDependenciesManagerCached(project) {
  override fun getModificationTracker(): FilesModificationTrackerBase {
    return PipEnvLockModificationTracker.getInstance(project)
  }

  override fun parseRequirements(requirementsFile: VirtualFile): List<PyRequirement>? {
    return getPipFileLockRequirements(requirementsFile)
  }

  override fun isAddDependencyPossible(): Boolean {
    return false
  }

  override fun addDependency(packageName: String): Boolean {
    return false
  }

  override fun getDependenciesFile(): VirtualFile? {
    return getPipFileLock(sdk)
  }

  companion object {
    private val KEY = Key<PipEnvDependenciesManager>(this::class.java.name)

    fun getInstance(project: Project, sdk: Sdk): PipEnvDependenciesManager = sdk.getOrCreateUserDataUnsafe(KEY) {
      PipEnvDependenciesManager(project, sdk).also {
        Disposer.register(PythonPluginDisposable.getInstance(project), it)
        Disposer.register(it, Disposable { sdk.putUserData(KEY, null) })
      }
    }
  }
}