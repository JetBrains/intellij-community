package com.intellij.python.pyproject.model.api

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.pyproject.model.internal.getModuleSdkStateImpl
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.findPythonSdk


// Each module might have either SDK or suggestion to configure it.

/**
 * Configures module SDK if it doesn't have one, returns either `null` if no SDK suggestion available or SDK result configuration.
 * This function is a recommended way to configure SDK: just use it.
 */
suspend fun Module.configureSdkIfNeeded(): SdkConfigurationResult<PyError>? =
  configureSdkIfNeeded { autoConfigureSdkCompletely() }

/**
 * Calls [onNoSdk] if module has no SDK but has a suggestion to configure SDK.
 * in this block call [autoConfigureSdkCompletely], [autoConfigureSdkDoNotCreateFiles] or [autoConfigureSdkExistingOnly].
 */
suspend fun <T : Any> Module.configureSdkIfNeeded(onNoSdk: suspend SdkForModuleConfigInstruction.() -> SdkConfigurationResult<T>): SdkConfigurationResult<T>? =
  when (val s = getModuleSdkState()) {
    is ModuleSdkState.HasSdk -> SdkConfigurationResult.Configured(s.sdk)
    is ModuleSdkState.NoSdk -> s.sdkConfigInstruction?.onNoSdk()
  }

/**
 * Each module either has an SDK or might have a suggestion to configure one.
 * You can build logic around this suggestion (aka [SdkForModuleConfigInstruction], but if you want to use it to configure SDK,
 * just use [Module.configureSdkIfNeeded]
 *
 * Returns [ModuleSdkState.HasSdk] when the module already has a Python SDK.
 * Otherwise, returns [ModuleSdkState.NoSdk] wrapping a suggested [SdkForModuleConfigInstruction],
 * or a `NoSdk` with `null` when the module isn't Python or no suggestion could be made.
 *
 * Suspends until the project model is fully loaded (via [findPythonSdk]) before checking,
 * so it is safe to call during startup without risking a false positive from a stale SDK table.
 *
 * For multiple calls, pull [configuratorsByTool] up not to create it each time.
 *
 * ```kotlin
 * when(val r = module.getModuleSdkState()) {
 *  is ModuleSdkState.HasSdk -> r.sdk....
 *  is ModuleSdkState.NoSdk -> r.sdkConfigInstruction.. // You can configure SDK with it
 * }
 * ```
 * If you only want to configure SDK if it doesn't exist, see [configureSdkIfNeeded]
 */
suspend fun Module.getModuleSdkState(
  configuratorsByTool: Map<ToolId, PyProjectSdkConfigurationExtension> = PyProjectSdkConfigurationExtension.createMap(),
  /**
   * Re-probe the configurators instead of reusing the shared cache. Only for a caller whose answer must be true at this
   * instant — one running under the SDK-configuration lock, where a sibling module may have been configured a moment
   * ago. Everything else wants the default: the probe runs the project's tools.
   */
  fresh: Boolean = false,
): ModuleSdkState = getModuleSdkStateImpl(configuratorsByTool, fresh)

/**
 * Result for [getModuleSdkState]: either SDK or suggestion on SDK creation
 */
sealed interface ModuleSdkState {
  /**
   * Module already has [sdk]
   */
  class HasSdk internal constructor(val sdk: Sdk) : ModuleSdkState

  /**
   * Module has no SDK, and it either has [sdkConfigInstruction] or `null` if module isn't python or no suggestion could be made.
   */
  class NoSdk internal constructor(val sdkConfigInstruction: SdkForModuleConfigInstruction?) : ModuleSdkState
}
