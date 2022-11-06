package com.jetbrains.python.sdk.flavors

import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.PySdkUtil

/**
 * Each [PythonSdkFlavor] is associated with [PyFlavorData].
 * For example: Conda flavor requires special data (conda binary path) to be stored in sdk additional data
 */
interface PyFlavorData {
  /**
   * Prepares [targetCommandLineBuilder] to run python on [sdk]
   */
  fun prepareTargetCommandLine(sdk: Sdk, targetCommandLineBuilder: TargetedCommandLineBuilder)

  /**
   * Some flavors do not need data at all: the only thing thy need is sdk homepath
   */
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