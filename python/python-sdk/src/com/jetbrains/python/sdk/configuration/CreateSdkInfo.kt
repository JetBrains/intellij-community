// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import java.nio.file.Path

typealias CheckToml = Boolean
typealias EnvExists = Boolean

fun interface SdkCreator {
  suspend fun createSdk(): PyResult<Sdk>
}

/**
 * Tool exists, so a caller can create an SDK using [sdkCreator]
 */
@ApiStatus.Internal
sealed interface CreateSdkInfoWithSdkCreator {
  val sdkCreator: SdkCreator
}

/**
 * Creates SDK for a module named [moduleName]. This does **not** affect the module itself but just sets a user-readable title.
 */
@ApiStatus.Internal
fun CreateSdkInfoWithSdkCreator.getSdkCreator(moduleName: @NlsSafe String): SdkCreator = {
  withContext(TraceContext(moduleName)) {
    sdkCreator.createSdk()
  }
}

/**
 * Creates SDK for a module named [moduleName]. This does **not** affect the module itself but just sets a user-readable title.
 */
@ApiStatus.Internal
suspend fun CreateSdkInfoWithSdkCreator.createSdk(moduleName: @NlsSafe String): PyResult<Sdk> =
  getSdkCreator(moduleName).createSdk()

@ApiStatus.Internal
sealed interface CreateSdkInfo :
  Comparable<CreateSdkInfo> {
  @get:IntentionName
  val intentionName: String


  /**
   * We want to preserve the initial order, but at the same time we'd like to have a sort order depending on the type of CreateSdkInfo
   */
  override fun compareTo(other: CreateSdkInfo): Int {
    return sortOrder.compareTo(other.sortOrder)
  }

  /**
   * Environment files exist on disk, we just need to create an sdk using [sdkCreator]
   */
  class ExistingEnv internal constructor(
    val pythonInfo: PythonInfo,
    override val intentionName: String,
    override val sdkCreator: SdkCreator,
  ) : CreateSdkInfo, CreateSdkInfoWithSdkCreator

  /**
   * No [toolToInstall] installed. Install it first, then try again.
   */
  class WillInstallTool internal constructor(
    val toolToInstall: String,
    val pathPersister: (Path) -> Unit,
    override val intentionName: @IntentionName String,
  ) : CreateSdkInfo

  /**
   * Required tool exists, but [sdkCreator] will also create files on disk.
   */
  class WillCreateEnv internal constructor(
    override val intentionName: String,
    override val sdkCreator: SdkCreator,
  ) : CreateSdkInfo, CreateSdkInfoWithSdkCreator

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

fun CreateSdkInfoWithSdkCreator.getSdkCreator(module: Module): SdkCreator =
  getSdkCreator(module.name)

suspend fun CreateSdkInfoWithSdkCreator.createSdk(module: Module): PyResult<Sdk> = createSdk(module.name)
