// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.requirementsTxt

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.findOrCreateFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.jetbrains.python.packaging.PyPackageRequirementsSettings
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.associatedModuleDir
import com.jetbrains.python.sdk.associatedModuleNioPath
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.pySdkAdditionalData
import com.jetbrains.python.sdk.pythonSdk
import org.jetbrains.annotations.ApiStatus
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.name

/**
 * Migrate from the module persistent path to sdk path
 */
@ApiStatus.Internal
object PythonRequirementTxtSdkUtils {
  /**
   * Resolves the requirements file explicitly stored for [sdk] ([PythonSdkAdditionalData.requiredTxtPath]).
   * Returns `null` when no path is stored, or the stored path cannot be resolved to an existing file.
   * The default when nothing is stored is intentionally left to the caller (it differs per package manager).
   */
  @JvmStatic
  fun resolvePersistedRequirementsFile(sdk: Sdk): VirtualFile? {
    val storedPath = sdk.pySdkAdditionalData.requiredTxtPath ?: return null
    if (storedPath.isAbsolute) {
      return VirtualFileManager.getInstance().findFileByNioPath(storedPath)
    }

    val associatedModuleFile = sdk.associatedModuleDir ?: return null
    // findFileByRelativePath expects '/' separators; a path persisted on Windows uses '\', so normalize it (PY-83135).
    return associatedModuleFile.findFileByRelativePath(FileUtil.toSystemIndependentName(storedPath.toString()))
  }

  @JvmStatic
  fun saveRequirementsTxtPath(project: Project, sdk: Sdk, path: Path) {
    val sdkModificator = sdk.sdkModificator
    val modifiedData = sdkModificator.sdkAdditionalData as? PythonSdkAdditionalData ?: return

    val associatedModulePath = sdk.associatedModuleNioPath
    val realPath = if (path.isAbsolute && associatedModulePath != null && path.startsWith(associatedModulePath)) {
      associatedModulePath.relativize(path)
    }
    else {
      path
    }

    modifiedData.requiredTxtPath = realPath
    if (ApplicationManager.getApplication().isDispatchThread) {
      runWriteAction {
        sdkModificator.commitChanges()
      }
    }
    else {
      PyPackageCoroutine.launch(project) {
        edtWriteAction {
          sdkModificator.commitChanges()
        }
      }
    }
  }

  fun createRequirementsTxtPath(module: Module, sdk: Sdk): VirtualFile? {
    val basePath = sdk.associatedModuleDir ?: module.baseDir ?: return null
    val requirementsFile = basePath.findOrCreateFile(PythonSdkAdditionalData.REQUIREMENT_TXT_DEFAULT.toString())

    //Need to pass test, because TempFS doesn't support getNioPath()
    val requirementFilePath = requirementsFile.toNioPathOrNull() ?: Path.of(requirementsFile.path)
    saveRequirementsTxtPath(module.project, sdk, requirementFilePath)

    return requirementsFile
  }


  fun migrateRequirementsTxtPathFromModuleToSdk(project: Project, sdk: Sdk) {
    val newPath = sdk.pySdkAdditionalData.requiredTxtPath
    if (newPath != null)
      return

    val originalPath = project.modules.firstNotNullOfOrNull {
      getRequirementsTxtFromModule(it)
    } ?: return

    val path = try {
      Path.of(originalPath)
    }
    catch (t: InvalidPathException) {
      thisLogger().warn(t)
      return
    }

    saveRequirementsTxtPath(project, sdk, path)
  }

  /**
   * Should be used if sdk is not setup
   */
  @JvmStatic
  fun detectRequirementsTxtInModule(module: Module): VirtualFile? {
    val requirementsPath = ModuleRootManager.getInstance(module).contentRoots.firstNotNullOfOrNull {
      it.findChild(PythonSdkAdditionalData.REQUIREMENT_TXT_DEFAULT.name)
    }

    return requirementsPath
  }

  @Suppress("DEPRECATION", "removal")
  private fun getRequirementsTxtFromModule(module: Module): String? {
    val settings = PyPackageRequirementsSettings.getInstance(module)

    if (module.pythonSdk == null)
      return null

    val requirementsPath = settings.state.myRequirementsPath

    return if (requirementsPath.isNotBlank() && requirementsPath != PythonSdkAdditionalData.REQUIREMENT_TXT_DEFAULT.toString()) {
      requirementsPath
    }
    else
      null
  }

}