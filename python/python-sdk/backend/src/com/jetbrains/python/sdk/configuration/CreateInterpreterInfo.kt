// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.configuration

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

typealias CheckToml = Boolean
typealias EnvExists = Boolean

fun interface InterpreterCreator {
  suspend fun createInterpreter(): PyResult<PythonInterpreter>
}

/**
 * Tool exists, so a caller can create an SDK using [interpreterCreator]
 */
@ApiStatus.Internal
sealed interface CreateInterpreterInfoWithInterpreterCreator {
  val interpreterCreator: InterpreterCreator
}

/**
 * Creates SDK for a module named [moduleName]. This does **not** affect the module itself but just sets a user-readable title.
 */
@ApiStatus.Internal
fun CreateInterpreterInfoWithInterpreterCreator.getInterpreterCreator(moduleName: @NlsSafe String): InterpreterCreator = {
  withContext(TraceContext(moduleName)) {
    interpreterCreator.createInterpreter()
  }
}

/**
 * Creates SDK for a module named [moduleName]. This does **not** affect the module itself but just sets a user-readable title.
 */
@ApiStatus.Internal
suspend fun CreateInterpreterInfoWithInterpreterCreator.createInterpreter(moduleName: @NlsSafe String): PyResult<PythonInterpreter> =
  getInterpreterCreator(moduleName).createInterpreter()

@ApiStatus.Internal
sealed interface CreateInterpreterInfo :
  Comparable<CreateInterpreterInfo> {
  @get:IntentionName
  val intentionName: String


  /**
   * We want to preserve the initial order, but at the same time we'd like to have a sort order depending on the type of CreateSdkInfo
   */
  override fun compareTo(other: CreateInterpreterInfo): Int {
    return sortOrder.compareTo(other.sortOrder)
  }

  /**
   * Environment files exist on disk, we just need to create an sdk using [interpreterCreator]
   */
  class ExistingEnv internal constructor(
    val pythonInfo: PythonInfo,
    override val intentionName: String,
    override val interpreterCreator: InterpreterCreator,
  ) : CreateInterpreterInfo, CreateInterpreterInfoWithInterpreterCreator

  /**
   * No [toolToInstall] installed. Install it first, then try again.
   */
  class WillInstallTool internal constructor(
    val toolToInstall: String,
    val pathPersister: (Path) -> Unit,
    override val intentionName: @IntentionName String,
  ) : CreateInterpreterInfo

  /**
   * Required tool exists, but [interpreterCreator] will also create files on disk.
   */
  class WillCreateEnv internal constructor(
    override val intentionName: String,
    override val interpreterCreator: InterpreterCreator,
  ) : CreateInterpreterInfo, CreateInterpreterInfoWithInterpreterCreator

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
  interpreterCreator: (EnvExists) -> InterpreterCreator,
): CreateInterpreterInfo? {
  return when (val res = envChecker()) {
    is EnvCheckerResult.EnvFound -> CreateInterpreterInfo.ExistingEnv(
      res.pythonInfo,
      res.intentionName,
      interpreterCreator(true)
    )
    is EnvCheckerResult.EnvNotFound -> CreateInterpreterInfo.WillCreateEnv(res.intentionName, interpreterCreator(false))
    is EnvCheckerResult.SuggestToolInstallation -> CreateInterpreterInfo.WillInstallTool(res.toolToInstall, res.pathPersister, res.intentionName)
    is EnvCheckerResult.CannotConfigure -> null
  }
}

fun CreateInterpreterInfoWithInterpreterCreator.getInterpreterCreator(module: Module): InterpreterCreator =
  getInterpreterCreator(module.name)

suspend fun CreateInterpreterInfoWithInterpreterCreator.createInterpreter(module: Module): PyResult<PythonInterpreter> = createInterpreter(module.name)
