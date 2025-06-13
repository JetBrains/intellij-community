// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.requirements

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.openapi.vfs.findOrCreateFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.jetbrains.python.packaging.PyPackageRequirementsSettings
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.PythonSdkAdditionalData.REQUIREMENT_TXT_DEFAULT
import com.jetbrains.python.sdk.associatedModulePath
import com.jetbrains.python.sdk.baseDir
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path


/**
 * Migrate from the module persistent path to sdk path
 */
@ApiStatus.Internal
object PythonRequirementTxtUtils {
  @JvmStatic
  fun findRequirementsTxt(sdk: Sdk): VirtualFile? {
    val data = sdk.getSdkAdditionalData() as? PythonSdkAdditionalData ?: return null
    val requirementsPath = data.requiredTxtPath ?: Path.of(REQUIREMENT_TXT_DEFAULT)
    if (requirementsPath.isAbsolute) {
      return VirtualFileManager.getInstance().findFileByNioPath(requirementsPath)
    }
    val associatedModuleFile = data.associatedModuleVirtualFile ?: return null
    return associatedModuleFile.findFileByRelativePath(requirementsPath.toString())
  }

  @JvmStatic
  fun saveRequirementsTxtPath(project: Project, sdk: Sdk, path: Path) {
    val sdkModificator = sdk.sdkModificator
    val modifiedData = sdkModificator.sdkAdditionalData as? PythonSdkAdditionalData ?: return

    val associatedModulePath = sdk.associatedModulePath?.let { Path.of(it) }
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
        writeAction {
          sdkModificator.commitChanges()
        }
      }
    }
  }

  fun createRequirementsTxtPath(module: Module, sdk: Sdk): VirtualFile? {
    val sdkAdditionalData = sdk.sdkAdditionalData as? PythonSdkAdditionalData ?: return null
    val basePathString = sdkAdditionalData.associatedModuleVirtualFile ?: module.baseDir ?: return null
    val requirementsFile = basePathString.findOrCreateFile(REQUIREMENT_TXT_DEFAULT)

    //Need to pass test, because TempFS doesn't support getNioPath()
    val requirementFilePath = requirementsFile.toNioPathOrNull() ?: Path.of(requirementsFile.path)
    saveRequirementsTxtPath(module.project, sdk, requirementFilePath)

    return requirementsFile
  }


  fun migrateRequirementsTxtPathFromModuleToSdk(project: Project, sdk: Sdk) {
    val sdkAdditionalData = sdk.sdkAdditionalData as? PythonSdkAdditionalData ?: return
    val newPath = sdkAdditionalData.requiredTxtPath
    if (newPath != null)
      return

    val originalPath = project.modules.firstNotNullOfOrNull {
      getRequirementsTxtFromModule(it)
    } ?: return

    val path = try {
      Path.of(originalPath)
    }
    catch (t: Throwable) {
      thisLogger().warn(t)
      return
    }

    saveRequirementsTxtPath(project, sdk, path)
  }

  @Suppress("DEPRECATION", "removal")
  private fun getRequirementsTxtFromModule(module: Module): String? {
    val settings = PyPackageRequirementsSettings.getInstance(module)

    val requirementsPath = settings.state.myRequirementsPath
    settings.state.myRequirementsPath = ""
    return if (requirementsPath.isNotBlank() && requirementsPath != REQUIREMENT_TXT_DEFAULT)
      requirementsPath
    else
      null
  }

  private val PythonSdkAdditionalData.associatedModuleVirtualFile: VirtualFile?
    get() {
      val associatedModulePath = associatedModulePath ?: return null
      val nioPath = Path.of(associatedModulePath)
      return VirtualFileManager.getInstance().findFileByNioPath(nioPath) ?: TempFileSystem.getInstance().findFileByNioFile(nioPath)
    }
}