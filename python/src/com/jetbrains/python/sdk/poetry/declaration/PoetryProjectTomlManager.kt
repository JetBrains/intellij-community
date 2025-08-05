// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry.declaration

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserDataUnsafe
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.PythonPluginDisposable
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.pyprojectToml.PyProjectTomlManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PoetryProjectTomlManager(project: Project, sdk: Sdk) : PyProjectTomlManager(project, sdk) {
  override fun parseRequirements(requirementsFile: VirtualFile): List<PyRequirement> {
    return PoetryPyProjectTomlParser.getDependencies(project, requirementsFile)
  }

  override fun isAddDependencyPossible(): Boolean {
    return false
  }

  override fun addDependency(packageName: String): Boolean {
    return false
  }

  companion object {
    private val KEY = Key<PoetryProjectTomlManager>(this::class.java.name)

    @JvmStatic
    fun getInstance(project: Project, sdk: Sdk): PoetryProjectTomlManager = sdk.getOrCreateUserDataUnsafe(KEY) {
      PoetryProjectTomlManager(project, sdk).also {
        Disposer.register(PythonPluginDisposable.getInstance(project), it)
        Disposer.register(it, Disposable { sdk.putUserData(KEY, null) })
      }
    }
  }
}