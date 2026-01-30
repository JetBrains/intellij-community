// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.black

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.Version
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.resolve.PackageAvailabilitySpec
import com.jetbrains.python.psi.resolve.isPackageAvailable
import com.jetbrains.python.pathValidation.PlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import com.jetbrains.python.pathValidation.validateExecutableFile
import com.jetbrains.python.pyi.PyiFileType
import org.jetbrains.annotations.SystemDependent
import java.nio.file.Path

class BlackFormatterUtil {
  companion object {
    val LOG: Logger = thisLogger()

    const val PACKAGE_NAME: String = "black"
    private val PACKAGE_SPEC: PackageAvailabilitySpec = PackageAvailabilitySpec("black", "black.Mode", "black.mode.Mode")

    val MINIMAL_LINE_RANGES_COMPATIBLE_VERSION: Version = Version(23, 11, 0)

    fun isFileApplicable(vFile: VirtualFile): Boolean {
      return vFile.fileType == PythonFileType.INSTANCE || vFile.fileType == PyiFileType.INSTANCE
    }

    fun isBlackFormatterInstalledOnProjectSdk(project: Project, sdk: Sdk?): Boolean {
      if (sdk == null) return false
      return isPackageAvailable(project, sdk, PACKAGE_SPEC)
    }

    fun detectBlackExecutable(): Path? {
      val name = when {
        SystemInfo.isWindows -> "black.exe"
        else -> "black"
      }
      return PathEnvironmentVariableUtil.findInPath(name)?.toPath()
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