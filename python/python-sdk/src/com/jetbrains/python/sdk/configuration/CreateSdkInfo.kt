package com.jetbrains.python.sdk.configuration

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.impl.PySdkBundle
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

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
   * We want to preserve the initial order, but at the same time we'd like to have a sort order depending on the type of CreateSdkInfo
   */
  override fun compareTo(other: CreateSdkInfo): Int {
    return sortOrder.compareTo(other.sortOrder)
  }

  class ExistingEnv internal constructor(
    val pythonInfo: PythonInfo,
    override val intentionName: String,
    sdkCreator: SdkCreator,
  ) : CreateSdkInfo(sdkCreator)

  class WillInstallTool internal constructor(
    val toolToInstall: String,
    val pathPersister: (Path) -> Unit,
    override val intentionName: @IntentionName String,
  ) : CreateSdkInfo(
    {
      /**
       * This specific CreateSdkInfo is only supposed to be used for proposing tool installation, it never should be used for SDK creation.
       */
      PyResult.localizedError(PySdkBundle.message("python.sdk.cannot.create.tool.should.be.installed"))
    }
  )

  class WillCreateEnv internal constructor(
    override val intentionName: String,
    sdkCreator: SdkCreator,
  ) : CreateSdkInfo(sdkCreator)

  private val sortOrder: Int
    get() = when (this) {
      is ExistingEnv -> 0
      is WillInstallTool -> 1
      is WillCreateEnv -> 2
    }
}

@ApiStatus.Internal
sealed interface EnvCheckerResult {
  data class EnvFound(val pythonInfo: PythonInfo, val intentionName: @IntentionName String) : EnvCheckerResult
  data class SuggestToolInstallation(
    val toolToInstall: String, val pathPersister: (Path) -> Unit, val intentionName: @IntentionName String,
  ) : EnvCheckerResult
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
    is EnvCheckerResult.SuggestToolInstallation -> CreateSdkInfo.WillInstallTool(res.toolToInstall, res.pathPersister, res.intentionName)
    is EnvCheckerResult.CannotConfigure -> null
  }
}

fun CreateSdkInfo.getSdkCreator(module: Module): SdkCreator =
  getSdkCreator(module.name)

suspend fun CreateSdkInfo.createSdk(module: Module): PyResult<Sdk> = createSdk(module.name)
