// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.requirementsTxt

import com.intellij.python.pyproject.model.evolution.findEvoPyProjectIfReady
import com.intellij.python.sdk.backend.writeRequirementsPath
import com.intellij.python.sdk.backend.requirementsPath
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.findOrCreateFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.packaging.PyPackageRequirementsSettings
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.associatedModuleNioPath
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.pySdkAdditionalData
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
   * Resolves the requirements file explicitly stored for [sdk] ([PythonSdkAdditionalData.requirementsFile]).
   * Returns `null` when no path is stored, or the stored path cannot be resolved to an existing file.
   * The default when nothing is stored is intentionally left to the caller (it differs per package manager).
   */
  @JvmStatic
  fun resolvePersistedRequirementsFile(sdk: Sdk): VirtualFile? {
    val requirementsPath = sdk.pySdkAdditionalData.requirementsPath ?: return null
    return VirtualFileManager.getInstance().findFileByNioPath(requirementsPath)
  }

  /**
   * The requirements file stored for [interpreter], see [PythonInterpreter.requirementsPath]. `null` when no file is
   * stored, or when the stored file does not exist.
   */
  fun resolvePersistedRequirementsFile(interpreter: PythonInterpreter): VirtualFile? {
    val requirementsPath = interpreter.requirementsPath ?: return null
    return VirtualFileManager.getInstance().findFileByNioPath(requirementsPath)
  }

  @JvmStatic
  fun saveRequirementsTxtPath(project: Project, sdk: Sdk, path: Path?) {
    val sdkModificator = sdk.sdkModificator
    val modifiedData = sdkModificator.sdkAdditionalData as? PythonSdkAdditionalData ?: return

    val requirementsPath = path?.let { requestedPath ->
      val basePath = modifiedData.workingDirectory.takeIf { modifiedData.hasValidWorkingDirectory() }
                     ?: sdk.associatedModuleNioPath
                     ?: project.basePath?.let { Path.of(it) }
                     ?: return
      if (requestedPath.isAbsolute) requestedPath else basePath.resolve(requestedPath)
    }
    modifiedData.requirementsPath = requirementsPath
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

  @RequiresWriteLock
  fun createRequirementsTxtPath(module: Module, interpreter: PythonInterpreter): VirtualFile? {
    val basePath = VirtualFileManager.getInstance().findFileByNioPath(interpreter.ownerDir) ?: module.baseDir ?: return null
    val requirementsFile = basePath.findOrCreateFile(PythonSdkAdditionalData.REQUIREMENT_TXT_DEFAULT.toString())

    //Need to pass test, because TempFS doesn't support getNioPath()
    val requirementFilePath = requirementsFile.toNioPathOrNull() ?: Path.of(requirementsFile.path)
    // A write action cannot suspend, so the store writes asynchronously.
    PyPackageCoroutine.launch(module.project) { interpreter.writeRequirementsPath(requirementFilePath) }

    return requirementsFile
  }


  /**
   * Moves the requirements file of the module setting of an older build to [interpreter], when [interpreter] stores none. The
   * write runs asynchronously.
   */
  fun migrateRequirementsTxtPathFromModule(interpreter: PythonInterpreter) {
    if (interpreter.requirementsPath != null)
      return
    val project = interpreter.project

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

    PyPackageCoroutine.launch(project) { interpreter.writeRequirementsPath(path) }
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

    if (module.findEvoPyProjectIfReady()?.interpreter == null)
      return null

    val requirementsPath = settings.state.myRequirementsPath

    return if (requirementsPath.isNotBlank() && requirementsPath != PythonSdkAdditionalData.REQUIREMENT_TXT_DEFAULT.toString()) {
      requirementsPath
    }
    else
      null
  }

}
