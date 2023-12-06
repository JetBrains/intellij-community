// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.black

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.pyi.PyiFileType
import com.jetbrains.python.pathValidation.PlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import com.jetbrains.python.pathValidation.validateExecutableFile
import org.jetbrains.annotations.SystemDependent
import java.io.File

class BlackFormatterUtil {
  companion object {
    val LOG = thisLogger()

    const val PACKAGE_NAME: String = "black"

    fun isFileApplicable(vFile: VirtualFile): Boolean {
      return vFile.fileType == PythonFileType.INSTANCE || vFile.fileType == PyiFileType.INSTANCE
    }

    fun isBlackFormatterInstalledOnProjectSdk(sdk: Sdk?): Boolean {
      val packageManager = sdk?.let { PyPackageManager.getInstance(it) }
      return packageManager?.let {
        it.packages?.any { pyPackage -> pyPackage.name == PACKAGE_NAME }
      } ?: false
    }

    fun getBlackFormatterPackageInfo(sdk: Sdk?): PyPackage? {
      val packageManager = sdk?.let { PyPackageManager.getInstance(it) }
      return packageManager?.let {
        it.refreshAndGetPackages(false).firstOrNull { pyPackage -> pyPackage.name == PACKAGE_NAME }
      }
    }

    fun detectBlackExecutable(): File? {
      val name = when {
        SystemInfo.isWindows -> "black.exe"
        else -> "black"
      }
      return PathEnvironmentVariableUtil.findInPath(name)
    }

    fun isBlackExecutableDetected(): Boolean = detectBlackExecutable() != null

    fun validateBlackExecutable(path: @SystemDependent String?): ValidationInfo? {
      return validateExecutableFile(ValidationRequest(
        path = path,
        fieldIsEmpty = PyBundle.message("black.executable.not.found", if (SystemInfo.isWindows) 0 else 1),
        platformAndRoot = PlatformAndRoot.local
      ))
    }
  }
}