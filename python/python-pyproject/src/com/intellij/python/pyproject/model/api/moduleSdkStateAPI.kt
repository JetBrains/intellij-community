package com.intellij.python.pyproject.model.api

import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.pyproject.model.internal.getModuleSdkStateImpl
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension


// Each Python project might have either an interpreter or a suggestion to configure one.

/**
 * Configures an interpreter for this project if it has none. Returns `null` when no suggestion is available, and the
 * configuration result otherwise.
 * This function is a recommended way to configure an interpreter: just use it.
 */
suspend fun PyProject.configureSdkIfNeeded(): InterpreterConfigurationResult<PyError>? =
  configureSdkIfNeeded { autoConfigureSdkCompletely() }

/**
 * Calls [onNoSdk] if this project has no interpreter but has a suggestion to configure one.
 * In this block call [autoConfigureSdkCompletely], [autoConfigureSdkDoNotCreateFiles] or [autoConfigureSdkExistingOnly].
 */
suspend fun <T : Any> PyProject.configureSdkIfNeeded(onNoSdk: suspend SdkForModuleConfigInstruction.() -> InterpreterConfigurationResult<T>): InterpreterConfigurationResult<T>? =
  when (val s = getModuleSdkState()) {
    is ModuleSdkState.HasSdk -> InterpreterConfigurationResult.Configured(s.interpreter)
    is ModuleSdkState.NoSdk -> s.sdkConfigInstruction?.onNoSdk()
  }

/**
 * Each project either has an interpreter or might have a suggestion to configure one.
 * You can build logic around this suggestion (aka [SdkForModuleConfigInstruction]), but if you want to use it to
 * configure an interpreter, just use [PyProject.configureSdkIfNeeded].
 *
 * Returns [ModuleSdkState.HasSdk] when the project already has an interpreter.
 * Otherwise, returns [ModuleSdkState.NoSdk] wrapping a suggested [SdkForModuleConfigInstruction],
 * or a `NoSdk` with `null` when no suggestion could be made.
 *
 * Suspends until the project model is fully loaded before checking, so it is safe to call during startup without
 * risking a false positive from a stale SDK table.
 *
 * For multiple calls, pull [configuratorsByTool] up not to create it each time.
 *
 * ```kotlin
 * when(val r = pyProject.getModuleSdkState()) {
 *  is ModuleSdkState.HasSdk -> r.interpreter....
 *  is ModuleSdkState.NoSdk -> r.sdkConfigInstruction.. // You can configure an interpreter with it
 * }
 * ```
 * If you only want to configure an interpreter if it doesn't exist, see [configureSdkIfNeeded]
 */
suspend fun PyProject.getModuleSdkState(
  configuratorsByTool: Map<ToolId, PyProjectSdkConfigurationExtension> = PyProjectSdkConfigurationExtension.createMap(),
  /**
   * Re-probe the configurators instead of reusing the shared cache. Only for a caller whose answer must be true at this
   * instant — one running under the SDK-configuration lock, where a sibling module may have been configured a moment
   * ago. Everything else wants the default: the probe runs the project's tools.
   */
  fresh: Boolean = false,
): ModuleSdkState = getModuleSdkStateImpl(configuratorsByTool, fresh)

/**
 * Result for [getModuleSdkState]: either an interpreter or a suggestion to configure one
 */
sealed interface ModuleSdkState {
  /**
   * The project already has [interpreter]
   */
  class HasSdk internal constructor(val interpreter: PythonInterpreter) : ModuleSdkState

  /**
   * The project has no interpreter, and it either has [sdkConfigInstruction] or `null` if no suggestion could be made.
   */
  class NoSdk internal constructor(val sdkConfigInstruction: SdkForModuleConfigInstruction?) : ModuleSdkState
}
