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

typealias CheckToml = Boolean
typealias EnvExists = Boolean

fun interface SdkCreator {
  suspend fun createSdk(): PyResult<Sdk>
}

@ApiStatus.Internal
sealed class CreateSdkInfo(private val sdkCreator: SdkCreator) :
  Comparable<CreateSdkInfo> {
  @get:IntentionName
  abstract val intentionName: String

  /**
   * Creates SDK for a module named [moduleName]. This does **not** affect the module itself but just sets a user-readable title.
   */
  fun getSdkCreator(moduleName: @NlsSafe String): SdkCreator = {
    withContext(TraceContext(moduleName)) {
      sdkCreator.createSdk()
    }
  }


  /**
   * Creates SDK for a module named [moduleName]. This does **not** affect the module itself but just sets a user-readable title.
   */
  suspend fun createSdk(moduleName: @NlsSafe String): PyResult<Sdk> =
    getSdkCreator(moduleName).createSdk()

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
suspend fun prepareSdkCreator(
  envChecker: suspend () -> EnvCheckerResult,
  sdkCreator: (EnvExists) -> SdkCreator,
): CreateSdkInfo? {
  return when (val res = envChecker()) {
    is EnvCheckerResult.EnvFound -> CreateSdkInfo.ExistingEnv(
      res.pythonInfo,
      res.intentionName,
      sdkCreator(true)
    )
    is EnvCheckerResult.EnvNotFound -> CreateSdkInfo.WillCreateEnv(res.intentionName, sdkCreator(false))
    is EnvCheckerResult.CannotConfigure -> null
  }
}

fun CreateSdkInfo.getSdkCreator(module: Module): SdkCreator =
  getSdkCreator(module.name)

suspend fun CreateSdkInfo.createSdk(module: Module): PyResult<Sdk> = createSdk(module.name)
