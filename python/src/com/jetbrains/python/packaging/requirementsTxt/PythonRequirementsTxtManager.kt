// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.requirementsTxt

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.FilesModificationTrackerBase
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserDataUnsafe
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.PythonPluginDisposable
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.packaging.dependencies.cache.PythonDependenciesManagerCached
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PythonRequirementsTxtManager private constructor(project: Project, val sdk: Sdk) : PythonDependenciesManagerCached(project) {
  override fun getDependenciesFile(): VirtualFile? = PythonRequirementTxtSdkUtils.findRequirementsTxt(sdk)

  override fun getModificationTracker(): FilesModificationTrackerBase {
    return RequirementTxtModificationTracker.getInstance(project)
  }

  override fun parseRequirements(requirementsFile: VirtualFile): List<PyRequirement> {
    return runReadAction { PyRequirementParser.fromFile(requirementsFile) }
  }

  override fun addDependency(packageName: String): Boolean {
    val virtualFile = getDependenciesFile() ?: return false
    return RequirementsTxtManipulationHelper.addToRequirementsTxt(project, virtualFile, packageName)
  }

  companion object {
    private val KEY = Key<PythonRequirementsTxtManager>(this::class.java.name)

    @JvmStatic
    fun getInstance(project: Project, sdk: Sdk): PythonRequirementsTxtManager = sdk.getOrCreateUserDataUnsafe(KEY) {
      PythonRequirementsTxtManager(project, sdk).also {
        Disposer.register(PythonPluginDisposable.getInstance(project), it)
        Disposer.register(it, Disposable { sdk.putUserData(KEY, null) })
      }
    }
  }
}