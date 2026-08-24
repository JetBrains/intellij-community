package com.intellij.python.pyproject.model.internal

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.pyproject.model.api.ModuleSdkState
import com.intellij.python.pyproject.model.api.SdkConfigurationResult
import com.intellij.python.pyproject.model.api.SdkForModuleConfigInstruction
import com.intellij.python.pyproject.model.api.autoConfigureSdkCompletely
import com.intellij.python.pyproject.model.api.autoConfigureSdkDoNotCreateFiles
import com.intellij.python.pyproject.model.api.autoConfigureSdkExistingOnly
import com.intellij.python.pyproject.model.api.getModuleSdkState
import com.intellij.python.pyproject.statistics.PyProjectTomlCollector
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithSdkCreator
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithToolBase
import com.jetbrains.python.sdk.configuration.getSdkCreator
import com.jetbrains.python.sdk.findPythonSdk
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.setAssociationToModule
import com.jetbrains.python.sdk.withSdkConfigurationLock

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
internal suspend fun <T : Any> SdkForModuleConfigInstruction.autoConfigureSdk(controller: AutoConfigurationController<T>): SdkConfigurationResult<T> =
  withSdkConfigurationLock(module.project) {
    // We might have TOCTOU here (sdk might already be created), so we check SDK once again
    module.findPythonSdk()?.let { currentSdk ->
      SdkConfigurationResult.Configured(currentSdk)
    } ?: autoConfigureSdkImpl(controller)
  }


/**
 * Controls SDK configuration process
 */
internal fun interface AutoConfigurationController<T : Any> {
  /**
   * We need to create an SDK (either SDK or files) with [infoWithTool] (see its fields).
   * Decision is returned as [SdkSetupCallBack]
   */
  fun onSdkSetupRequired(infoWithTool: CreateSdkInfoWithToolBase<CreateSdkInfoWithSdkCreator>): SdkSetupCallBack<T>
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
private suspend fun <T : Any> SdkForModuleConfigInstruction.autoConfigureSdkImpl(controller: AutoConfigurationController<T>): SdkConfigurationResult<T> =
  when (this) {
    is SdkForModuleConfigInstruction.CreateSdkInfoWrapper -> {
      when (val r = this.createSdkInfoWithTool.createSdkInfo) {
        is CreateSdkInfoWithSdkCreator -> {
          // SDK needs to be created
          when (val sdkSetup = controller.onSdkSetupRequired(CreateSdkInfoWithToolBase(r, toolId))) {
            is SdkSetupCallBack.Accepted -> {
              // Allowed by a controller
              when (val createSdk = r.getSdkCreator(module).createSdk()) {
                // Creation failed
                is Result.Failure -> SdkConfigurationResult.NotConfigured(sdkSetup.sdkResultMapper(createSdk.error))
                is Result.Success -> {
                  // Creation success, save it
                  val sdk = createSdk.result
                  module.pythonSdk = sdk
                  sdk.setAssociationToModule(module)
                  PyProjectTomlCollector.sdkCreatedAutomatically(toolId)
                  SdkConfigurationResult.Configured(sdk)
                }
              }
            }
            // Denied by a controller
            is SdkSetupCallBack.Denied -> SdkConfigurationResult.NotConfigured(sdkSetup.reason)
          }
        }
        // We do not install tools automatically
        is CreateSdkInfo.WillInstallTool -> SdkConfigurationResult.ToolNotInstalled(r)
      }
    }
    is SdkForModuleConfigInstruction.SameAs -> { // Same as a parent module

      // Save SDK, but not assoc it with module as it is a parent SDK
      fun Sdk.asSdkResult(): SdkConfigurationResult.Configured<T> {
        module.pythonSdk = this@asSdkResult
        return SdkConfigurationResult.Configured(this)
      }

      check(parentModule != module) { "$parentModule can't be parent of the same module $module" }
      when (val parentSdkResult = parentModule.getModuleSdkState()) {
        is ModuleSdkState.HasSdk -> {
          // Parent already has SDK
          parentSdkResult.sdk.asSdkResult()
        }
        is ModuleSdkState.NoSdk -> {
          val parentSdkInfo = parentSdkResult.sdkConfigInstruction
          val error = if (parentSdkInfo == null) {
            null // Parent module has no SDK config info
          }
          else {
            // It is important to use sdkImpl as it doesn't lock a mutex which is already taken
            when (val parentSdkResult = parentSdkInfo.autoConfigureSdkImpl(controller)) {
              is SdkConfigurationResult.ToolNotInstalled,
              is SdkConfigurationResult.ParentHasNoSdk,
              is SdkConfigurationResult.NotConfigured,
                -> parentSdkResult // Parent has problems with SDK, return it
              is SdkConfigurationResult.Configured -> {
                // Parent SDK was configured
                return parentSdkResult.sdk.asSdkResult()
              }
            }
          }
          // Report parent SDK can't be configured
          SdkConfigurationResult.ParentHasNoSdk(parentModule, error)
        }
      }
    }
  }
