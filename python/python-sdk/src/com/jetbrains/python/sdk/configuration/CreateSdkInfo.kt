package com.jetbrains.python.sdk.configuration

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

typealias CheckExistence = Boolean
typealias CheckToml = Boolean
typealias EnvExists = Boolean

fun interface SdkCreator {
  suspend fun createSdk(needsConfirmation: Boolean): PyResult<Sdk?>
}

@ApiStatus.Internal
sealed class CreateSdkInfo(private val sdkCreator: SdkCreator) :
  Comparable<CreateSdkInfo> {
  @get:IntentionName
  abstract val intentionName: String

  /**
   * Creates SDK for a module named [moduleName]. This does **not** affect the module itself, but just sets user readable title.
   */
  fun getSdkCreator(moduleName: @NlsSafe String): SdkCreator = {
    withContext(TraceContext(moduleName)) {
      sdkCreator.createSdk(it)
    }
  }


  /**
   * Nullable SDK is only possible when we requested user confirmation but didn't get it. The idea behind this function is to provide
   * non-nullable SDK when no confirmation from the user is needed.
   *
   * It's a temporary solution until we'll be able to remove all custom user dialogs and enable [enableSDKAutoConfigurator] by default.
   * After that lands, we'll get rid of nullable SDK.
   *
   * [moduleName] does **not** affect the module itself, but just sets user readable title.
   */
  suspend fun createSdkWithoutConfirmation(moduleName: @NlsSafe String): PyResult<Sdk> =
    getSdkCreator(moduleName).createSdk(needsConfirmation = false).mapSuccess { it!! }

  /**
   * We want to preserve the initial order, but at the same time existing environment should have a higher priority by default
   */
  override fun compareTo(other: CreateSdkInfo): Int {
    val thisExists = if (this is ExistingEnv) 0 else 1
    val otherExists = if (other is ExistingEnv) 0 else 1
    return thisExists.compareTo(otherExists)
  }

  class ExistingEnv internal constructor(
    val pythonInfo: PythonInfo,
    override val intentionName: String,
    sdkCreator: SdkCreator,
  ) : CreateSdkInfo(sdkCreator)

  class WillCreateEnv internal constructor(
    override val intentionName: String,
    sdkCreator: SdkCreator,
  ) : CreateSdkInfo(sdkCreator)
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
  sdkCreator: (EnvExists) -> SdkCreator,
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

fun CreateSdkInfo.getSdkCreator(module: Module): SdkCreator =
  getSdkCreator(module.name)

suspend fun CreateSdkInfo.createSdkWithoutConfirmation(module: Module): PyResult<Sdk> = createSdkWithoutConfirmation(module.name)