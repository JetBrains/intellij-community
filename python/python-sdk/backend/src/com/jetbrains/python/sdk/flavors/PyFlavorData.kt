package com.jetbrains.python.sdk.flavors

import com.intellij.execution.ExecutionException
import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.onFailure
import com.jetbrains.python.sdk.activationEnvironment
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import org.jetbrains.annotations.ApiStatus

/**
 * Each [PythonSdkFlavor] is associated with [PyFlavorData].
 * For example: Conda flavor requires special data (conda binary path) to be stored in sdk additional data
 */
interface PyFlavorData {
  /**
   * Prepares [targetCommandLineBuilder] to run python on [sdk]
   */
  @RequiresBackgroundThread
  @Throws(ExecutionException::class)
  fun prepareTargetCommandLine(sdk: Sdk, targetCommandLineBuilder: TargetedCommandLineBuilder)

  @ApiStatus.Internal
  @PyInternalExecApi
  object Utils {
    @Throws(ExecutionException::class)
    @RequiresBackgroundThread
    fun prepareTargetCommandLine(
      sdk: Sdk,
      targetCommandLineBuilder: TargetedCommandLineBuilder,
      additionalCommands: (FullPathOnTarget) -> PyResult<Unit> = { PyResult.success(Unit) },
    ) {
      val interpreterPath = sdk.homePath!!
      targetCommandLineBuilder.setExePath(interpreterPath)
      additionalCommands(interpreterPath).onFailure {
        throw ExecutionException(it.message)
      }
      if (!PythonSdkUtil.isRemote(sdk)) {
        runBlockingMaybeCancellable { sdk.activationEnvironment() }
      }
    }

  }

  /**
   * The flavor data object which is suitable for running Python scripts on CPython, Python venvs, Maya and other interpreters.
   *
   * Running code on Conda interpreter with this flavor data might have issues as Conda has its own way to activate interpreter environment.
   */
  object Empty : PyFlavorData {
    @RequiresBackgroundThread
    override fun prepareTargetCommandLine(
      sdk: Sdk,
      targetCommandLineBuilder: TargetedCommandLineBuilder,
    ): Unit = Utils.prepareTargetCommandLine(sdk, targetCommandLineBuilder)
  }
}