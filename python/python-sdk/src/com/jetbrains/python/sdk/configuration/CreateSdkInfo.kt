package com.jetbrains.python.sdk.configuration

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.errorProcessing.PyResult
import org.jetbrains.annotations.ApiStatus

typealias NeedsConfirmation = Boolean
typealias CheckExistence = Boolean
typealias CheckToml = Boolean
typealias EnvExists = Boolean

@ApiStatus.Internal
sealed interface CreateSdkInfo : Comparable<CreateSdkInfo> {
  @get:IntentionName
  val intentionName: String
  val sdkCreator: suspend (needsConfirmation: NeedsConfirmation) -> PyResult<Sdk?>

  /**
   * Nullable SDK is only possible when we requested user confirmation but didn't get it. The idea behind this function is to provide
   * non-nullable SDK when no confirmation from the user is needed.
   *
   * It's a temporary solution until we'll be able to remove all custom user dialogs and enable [enableSDKAutoConfigurator] by default.
   * After that lands, we'll get rid of nullable SDK.
   */
  suspend fun createSdkWithoutConfirmation(): PyResult<Sdk> = sdkCreator(false).mapSuccess { it!! }

  /**
   * We want to preserve the initial order, but at the same time existing environment should have a higher priority by default
   */
  override fun compareTo(other: CreateSdkInfo): Int {
    val thisExists = if (this is ExistingEnv) 0 else 1
    val otherExists = if (other is ExistingEnv) 0 else 1
    return thisExists.compareTo(otherExists)
  }

  data class ExistingEnv(
    val pythonInfo: PythonInfo,
    override val intentionName: String,
    override val sdkCreator: suspend (needsConfirmation: NeedsConfirmation) -> PyResult<Sdk?>,
  ) : CreateSdkInfo

  data class WillCreateEnv(
    override val intentionName: String,
    override val sdkCreator: suspend (needsConfirmation: NeedsConfirmation) -> PyResult<Sdk?>,
  ) : CreateSdkInfo
}

@ApiStatus.Internal
sealed interface EnvCheckerResult {
  data class EnvFound(val pythonInfo: PythonInfo, val intentionName: @IntentionName String) : EnvCheckerResult
  data class EnvNotFound(val intentionName: @IntentionName String) : EnvCheckerResult
  object CannotConfigure : EnvCheckerResult
}

@ApiStatus.Internal
// TODO: Make internal after we drop WSL sdk configurator
suspend fun prepareSdkCreator(
  envChecker: suspend (CheckExistence) -> EnvCheckerResult,
  sdkCreator: (EnvExists) -> (suspend (needsConfirmation: NeedsConfirmation) -> PyResult<Sdk?>),
): CreateSdkInfo? {
  var res = envChecker(true)
  return when (res) {
    is EnvCheckerResult.EnvFound -> CreateSdkInfo.ExistingEnv(
      res.pythonInfo,
      res.intentionName,
      sdkCreator(true)
    )
    is EnvCheckerResult.EnvNotFound -> {
      res = envChecker(false)
      when (res) {
        is EnvCheckerResult.EnvNotFound -> CreateSdkInfo.WillCreateEnv(res.intentionName, sdkCreator(false))
        is EnvCheckerResult.EnvFound -> throw AssertionError("Env shouldn't exist if we didn't check for it")
        is EnvCheckerResult.CannotConfigure -> null
      }
    }
    is EnvCheckerResult.CannotConfigure -> null
  }
}
