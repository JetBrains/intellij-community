package com.jetbrains.python.sdk.flavors

import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.remote.RemoteSdkPropertiesPaths
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.PythonSdkUtil

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
   * The flavor data object which is suitable for running Python scripts on CPython, Python venvs, Maya and other interpreters.
   *
   * Running code on Conda interpreter with this flavor data might have issues as Conda has its own way to activate interpreter environment.
   */
  object Empty : PyFlavorData {
    override fun prepareTargetCommandLine(sdk: Sdk, targetCommandLineBuilder: TargetedCommandLineBuilder) {
      val interpreterPath = getInterpreterPath(sdk)
      if (interpreterPath.isNullOrBlank()) {
        throw IllegalArgumentException("Sdk ${sdk} doesn't have interpreter path set")
      }
      targetCommandLineBuilder.setExePath(interpreterPath)
      if (!PythonSdkUtil.isRemote(sdk)) {
        PySdkUtil.activateVirtualEnv(sdk)
      }
    }

    /**
     * Returns the path to Python interpreter executable. The path is on the target environment.
     */
    private fun getInterpreterPath(sdk: Sdk): String? {
      // `RemoteSdkPropertiesPaths` suits both `PyRemoteSdkAdditionalDataBase` and `PyTargetAwareAdditionalData`
      return sdk.sdkAdditionalData?.let { (it as? RemoteSdkPropertiesPaths)?.interpreterPath } ?: sdk.homePath
    }
  }
}