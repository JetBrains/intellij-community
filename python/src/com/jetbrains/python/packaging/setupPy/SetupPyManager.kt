// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.setupPy

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.FilesModificationTrackerBase
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserDataUnsafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.jetbrains.python.PythonPluginDisposable
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.dependencies.cache.PythonDependenciesManagerCached
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.sdk.associatedModuleDir
import com.jetbrains.python.sdk.sdkFlavor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SetupPyManager private constructor(project: Project, val sdk: Sdk) : PythonDependenciesManagerCached(project) {
  override fun getDependenciesFile(): VirtualFile? {
    val moduleDir = sdk.associatedModuleDir ?: return null
    val virtualFile = moduleDir.findFileByRelativePath(SETUP_PY) ?: return null
    return virtualFile
  }

  fun getRequirementsPsiFile(): PyFile? {
    val file = getDependenciesFile() ?: return null
    return runReadAction {
      PsiManager.getInstance(project).findFile(file) as? PyFile
    }
  }

  override fun getModificationTracker(): FilesModificationTrackerBase {
    return SetupPyModificationTracker.getInstance(project)
  }

  override fun parseRequirements(requirementsFile: VirtualFile): List<PyRequirement>? {
    val psiFile = convertToPsiFile(requirementsFile) ?: return null
    val parseSetupPy = SetupPyHelpers.parseSetupPy(psiFile) ?: return null
    return parseSetupPy
  }


  override fun addDependency(packageName: String): Boolean {
    val file = getRequirementsPsiFile() ?: return false
    val languageLevel = sdk.sdkFlavor.getLanguageLevel(sdk)
    return SetupPyHelpers.addRequirementsToSetupPy(file, packageName, languageLevel)
  }


  private fun convertToPsiFile(requirementsFile: VirtualFile): PyFile? = runReadAction {
    PsiManager.getInstance(project).findFile(requirementsFile) as? PyFile
  }

  companion object {
    private val KEY = Key<SetupPyManager>(this::class.java.name)

    @ApiStatus.Internal
    const val SETUP_PY: String = SetupPyHelpers.SETUP_PY

    @JvmStatic
    fun getInstance(project: Project, sdk: Sdk): SetupPyManager = sdk.getOrCreateUserDataUnsafe(KEY) {
      SetupPyManager(project, sdk).also {
        Disposer.register(PythonPluginDisposable.getInstance(project), it)
        Disposer.register(it, Disposable { sdk.putUserData(KEY, null) })
      }
    }
  }
}