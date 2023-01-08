package com.jetbrains.python.sdk.flavors

import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.PySdkUtil

interface PyFlavorData {
  /**
   * Prepares [targetCommandLineBuilder] to run python on [sdk]
   */
  fun prepareTargetCommandLine(sdk: Sdk, targetCommandLineBuilder: TargetedCommandLineBuilder)

  object Empty : PyFlavorData {
    override fun prepareTargetCommandLine(sdk: Sdk, targetCommandLineBuilder: TargetedCommandLineBuilder) {
      if (sdk.homePath.isNullOrBlank()) {
        throw IllegalArgumentException("Sdk ${sdk} doesn't have home path set")
      }
      targetCommandLineBuilder.setExePath(sdk.homePath!!)
      PySdkUtil.activateVirtualEnv(sdk)
    }
  }
}