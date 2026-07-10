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

// TODO PY-87712 Move to a separate storage
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
    targetCommandLineBuilder.addEnvironmentVariable("UV_PROJECT_ENVIRONMENT", venvPath)
    if (!PythonSdkUtil.isRemote(sdk)) {
      PySdkUtil.activateVirtualEnv(sdk)
    }
  }
}