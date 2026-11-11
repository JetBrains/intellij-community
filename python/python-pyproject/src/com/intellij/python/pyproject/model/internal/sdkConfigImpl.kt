package com.intellij.python.pyproject.model.internal

import com.intellij.python.pyproject.model.evolution.getInterpreter
import com.intellij.python.pyproject.model.api.ModuleSdkState
import com.intellij.python.pyproject.model.api.InterpreterConfigurationError
import com.intellij.python.pyproject.model.api.InterpreterConfigurationResult
import com.intellij.python.pyproject.model.api.SdkForModuleConfigInstruction
import com.intellij.python.pyproject.model.api.autoConfigureSdkCompletely
import com.intellij.python.pyproject.model.api.autoConfigureSdkDoNotCreateFiles
import com.intellij.python.pyproject.model.api.autoConfigureSdkExistingOnly
import com.intellij.python.pyproject.model.api.getModuleSdkState
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.pyproject.statistics.PyProjectTomlCollector
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.jetbrains.python.Result
import com.jetbrains.python.onFailure
import com.intellij.openapi.diagnostic.fileLogger
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.sdk.configuration.CreateInterpreterInfo
import com.jetbrains.python.sdk.configuration.CreateInterpreterInfoWithInterpreterCreator
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithToolBase
import com.jetbrains.python.sdk.configuration.getInterpreterCreator
import com.intellij.python.pyproject.model.evolution.readStoredInterpreter
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.sdk.withSdkConfigurationLock

private val logger = fileLogger()

/**
 * Autoconfigures a Python SDK for the module.
 * The process is controlled by [controller] that should decide if it is allowed to create files or SDK (or only use existing one)
 * and maps result so you can pattern-match it.
 *
 * This is a low-level API, you are encouraged to use :
 * * [autoConfigureSdkCompletely]
 * * [autoConfigureSdkExistingOnly]
 * * [autoConfigureSdkDoNotCreateFiles]
 *
 * Also, see these functions for [AutoConfigurationController] design examples.
 *
 * To be called with [withSdkConfigurationLock] **only**!
 */
internal suspend fun <T : Any> SdkForModuleConfigInstruction.autoConfigureSdk(controller: AutoConfigurationController<T>): InterpreterConfigurationResult<T> {
  val outcome = withSdkConfigurationLock(module.project) {
    // We might have TOCTOU here (sdk might already be created), so we check SDK once again
    module.readStoredInterpreter()?.let { ConfigOutcome.Configured(it) } ?: autoConfigureSdkImpl(controller)
  }
  return when (outcome) {
    is ConfigOutcome.Configured -> {
      // A parent that `SameAs` configured on the way is written too, so the snapshot must hold it as well.
      val written = listOfNotNull(pyProject, (this as? SdkForModuleConfigInstruction.SameAs)?.parent)
      val model = EvoPyProjectModel.getInstance(module.project)
      model.awaitInterpreterOf(written)
      // The wait above ends only when the snapshot holds the interpreter the module holds now.
      val interpreter = checkNotNull(pyProject.getInterpreter()) {
        "${pyProject.residesOnModule} has no interpreter in the snapshot after it was configured"
      }
      InterpreterConfigurationResult.Configured(interpreter)
    }
    is ConfigOutcome.Failed -> when (val error = outcome.error) {
      is InterpreterConfigurationResult.NotConfigured -> error
      is InterpreterConfigurationResult.ToolNotInstalled -> error
      is InterpreterConfigurationResult.ParentHasNoInterpreter -> error
    }
  }
}

/**
 * What [autoConfigureSdkImpl] ends with, while the configuration lock is held.
 *
 * A configured SDK is not a [InterpreterConfigurationResult] yet: that result carries the interpreter from the snapshot, and
 * [autoConfigureSdk] waits for the snapshot only after it releases the lock.
 */
private sealed interface ConfigOutcome<T : Any> {
  /**
   * The module holds [interpreter] now. A `SameAs` child sets the [interpreter] of its parent, because the snapshot
   * does not hold a parent configured in the same call yet.
   */
  class Configured<T : Any>(val interpreter: PythonInterpreter) : ConfigOutcome<T>
  class Failed<T : Any>(val error: InterpreterConfigurationError<T>) : ConfigOutcome<T>
}

/**
 * Controls SDK configuration process
 */
internal fun interface AutoConfigurationController<T : Any> {
  /**
   * We need to create an SDK (either SDK or files) with [infoWithTool] (see its fields).
   * Decision is returned as [SdkSetupCallBack]
   */
  fun onSdkSetupRequired(infoWithTool: CreateSdkInfoWithToolBase<CreateInterpreterInfoWithInterpreterCreator>): SdkSetupCallBack<T>
}

internal sealed interface SdkSetupCallBack<T : Any> {
  /**
   * SDK creation is not allowed due to [reason]
   */
  @ConsistentCopyVisibility
  data class Denied<T : Any> internal constructor(val reason: T) : SdkSetupCallBack<T>

  /**
   * SDK creation is allowed, but when it failed, error mapped using [sdkResultMapper]
   */
  @ConsistentCopyVisibility
  data class Accepted<T : Any> internal constructor(val sdkResultMapper: (PyError) -> T) : SdkSetupCallBack<T>
}


/**
 * Call with [withSdkConfigurationLock]. It can't use it internally as it is recursive, and [kotlinx.coroutines.sync.Mutex] is not
 * reenterable.
 */
private suspend fun <T : Any> SdkForModuleConfigInstruction.autoConfigureSdkImpl(controller: AutoConfigurationController<T>): ConfigOutcome<T> =
  when (this) {
    is SdkForModuleConfigInstruction.CreateSdkInfoWrapper -> {
      when (val r = this.createSdkInfoWithTool.createSdkInfo) {
        is CreateInterpreterInfoWithInterpreterCreator -> {
          // SDK needs to be created
          when (val sdkSetup = controller.onSdkSetupRequired(CreateSdkInfoWithToolBase(r, toolId))) {
            is SdkSetupCallBack.Accepted -> {
              // Allowed by a controller
              when (val createSdk = r.getInterpreterCreator(module).createInterpreter()) {
                // Creation failed
                is Result.Failure -> ConfigOutcome.Failed(InterpreterConfigurationResult.NotConfigured(sdkSetup.sdkResultMapper(createSdk.error)))
                is Result.Success -> {
                  // Creation success, save it. autoConfigureSdk waits for the snapshot once, after the lock.
                  when (val set = pyProject.setPythonInterpreter(createSdk.result, waitForSnapshot = false)) {
                    is Result.Failure -> ConfigOutcome.Failed(InterpreterConfigurationResult.NotConfigured(sdkSetup.sdkResultMapper(set.error)))
                    is Result.Success -> {
                      PyProjectTomlCollector.sdkCreatedAutomatically(toolId)
                      ConfigOutcome.Configured(createSdk.result)
                    }
                  }
                }
              }
            }
            // Denied by a controller
            is SdkSetupCallBack.Denied -> ConfigOutcome.Failed(InterpreterConfigurationResult.NotConfigured(sdkSetup.reason))
          }
        }
        // We do not install tools automatically
        is CreateInterpreterInfo.WillInstallTool -> ConfigOutcome.Failed(InterpreterConfigurationResult.ToolNotInstalled(r))
      }
    }
    is SdkForModuleConfigInstruction.SameAs -> { // Same as a parent module

      // Use the parent interpreter, but do not associate it with this module, as it belongs to the parent. The parent
      // registered it, so the call only points this module at it.
      suspend fun inheritFromParent(interpreter: PythonInterpreter): ConfigOutcome.Configured<T> {
        pyProject.setPythonInterpreter(interpreter, waitForSnapshot = false)
          .onFailure { logger.warn("Cannot inherit the interpreter of ${parent.baseDir}: $it") }
        return ConfigOutcome.Configured(interpreter)
      }

      // Deliberately not the shared cache: this runs under the SDK-configuration lock, inside a loop that configures the
      // project's modules one by one, so the parent's SDK may have been created moments ago by an earlier iteration —
      // the same TOCTOU the check in `autoConfigureSdk` above guards against.
      when (val parentSdkResult = parent.getModuleSdkState(fresh = true)) {
        // Parent already has SDK
        is ModuleSdkState.HasSdk -> inheritFromParent(parentSdkResult.interpreter)
        is ModuleSdkState.NoSdk -> {
          val parentSdkInfo = parentSdkResult.sdkConfigInstruction
          val error = if (parentSdkInfo == null) {
            null // Parent module has no SDK config info
          }
          else {
            // It is important to use sdkImpl as it doesn't lock a mutex which is already taken
            when (val parentSdkResult = parentSdkInfo.autoConfigureSdkImpl(controller)) {
              // Parent has problems with SDK, return it
              is ConfigOutcome.Failed -> parentSdkResult.error
              // Parent SDK was configured in this call
              is ConfigOutcome.Configured -> return inheritFromParent(parentSdkResult.interpreter)
            }
          }
          // Report parent SDK can't be configured
          ConfigOutcome.Failed(InterpreterConfigurationResult.ParentHasNoInterpreter(parent, error))
        }
      }
    }
  }
