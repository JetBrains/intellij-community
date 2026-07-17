package com.jetbrains.python.sdk.uv

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.remote.RemoteSdkPropertiesPaths
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * TODO PY-87712 Should drop as a whole
 * uvWorkingDirectory - workingDirectory in PythonSdkAdditionalData
 * usePip - can be deduced based on requirementsFile in PythonSdkAdditionalData
 * venvPath - sdkHome
 * uvPath - stored as a setting for local EELs, for targets we can use detection only
 */
@ApiStatus.Internal
data class UvSdkFlavorData(
  val uvWorkingDirectory: Path?,
  val usePip: Boolean?,
  val venvPath: FullPathOnTarget?,
  val uvPath: FullPathOnTarget?,
) : PyFlavorData {

  override fun prepareTargetCommandLine(sdk: Sdk, targetCommandLineBuilder: TargetedCommandLineBuilder) {
    val interpreterPath = sdk.sdkAdditionalData?.let { (it as? RemoteSdkPropertiesPaths)?.interpreterPath } ?: sdk.homePath
    if (interpreterPath.isNullOrBlank()) {
      throw IllegalArgumentException("Sdk ${sdk} doesn't have interpreter path set")
    }
    targetCommandLineBuilder.setExePath(interpreterPath)
    val separator = targetCommandLineBuilder.request.targetPlatform.platform.fileSeparator
    targetCommandLineBuilder.addEnvironmentVariable("UV_PROJECT_ENVIRONMENT", interpreterPath.parentPath(separator).parentPath(separator))
    if (!PythonSdkUtil.isRemote(sdk)) {
      PySdkUtil.activateVirtualEnv(sdk)
    }
  }

  private fun String.parentPath(separator: Char): String = removeSuffix(separator.toString()).substringBeforeLast(separator)
}