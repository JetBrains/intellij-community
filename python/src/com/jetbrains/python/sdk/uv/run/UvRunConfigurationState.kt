// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.run

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle
import com.jetbrains.python.Result
import com.jetbrains.python.getOrNull
import com.jetbrains.python.onFailure
import com.jetbrains.python.run.PythonCommandLineState
import com.jetbrains.python.run.PythonExecution
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import com.jetbrains.python.sdk.associatedModulePath
import com.jetbrains.python.sdk.uv.ScriptSyncCheckResult
import com.jetbrains.python.sdk.uv.UvLowLevel
import com.jetbrains.python.sdk.uv.impl.createUvCli
import com.jetbrains.python.sdk.uv.impl.createUvLowLevel
import com.jetbrains.python.sdk.uv.impl.getUvExecutable
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
class UvRunConfigurationState(
  val uvRunConfiguration: UvRunConfiguration,
  env: ExecutionEnvironment,
  val project: Project,
) : PythonCommandLineState(uvRunConfiguration, env) {
  private val logger = fileLogger()

  @RequiresEdt
  override fun canRun(): Boolean {
    return canRun(
      project,
      uvRunConfiguration.options,
      UvSyncWarningDialogFactoryImpl(),
      logger
    )
  }

  override fun buildPythonExecution(helpersAwareRequest: HelpersAwareTargetEnvironmentRequest): PythonExecution {
    return buildUvRunConfigurationCli(uvRunConfiguration.options, isDebug)
  }
}

@ApiStatus.Internal
@RequiresEdt
fun canRun(
  project: Project,
  options: UvRunConfigurationOptions,
  syncWarningFactory: UvSyncWarningDialogFactory,
  logger: Logger,
): Boolean {
  // force save to make sure that commands read the most up-to-date pyproject.toml
  FileDocumentManager.getInstance().saveAllDocuments()

  // if the check is disabled, then we can run the configuration immediately
  if (!options.checkSync) {
    return true
  }

  val associatedModulePath = options.uvSdk?.associatedModulePath
  val uvExecutable = getUvExecutable()
  var isError = false
  var isUnsynced = false

  if (associatedModulePath != null && uvExecutable != null) {
    runWithModalProgressBlocking(project, PyBundle.message("uv.run.configuration.state.progress.name")) {
      val uv = createUvLowLevel(Path.of(associatedModulePath), createUvCli(uvExecutable))

      when (requiresSync(uv, options, logger).getOrNull()) {
        true -> isUnsynced = true
        false -> {}
        null -> isError = true
      }
    }
  } else {
    isError = true
  }

  if (isError || isUnsynced) {
    return syncWarningFactory.showAndGet(isError, options)
  }

  return true
}

@ApiStatus.Internal
suspend fun requiresSync(
  uv: UvLowLevel,
  options: UvRunConfigurationOptions,
  logger: Logger,
): Result<Boolean, Unit> {
  // module scenarios:
  // 1. module with --no-project flag -- no sync
  // 2. module -- syncs project
  //
  // script scenarios:
  // 1. script with no metadata -- syncs project
  // 2. script with no metadata and --no-project flag -- no sync
  // 3. script with metadata -- syncs script deps, doesn't sync project
  // 4. script with metadata and --no-project flag -- syncs script deps, doesn't sync project (flag is ignored)

  val containsExact = !options.uvArgs.contains("--exact")
  var hasNoMetadata = options.runType == UvRunType.MODULE // modules have no script metadata, so it starts with true in case of a module

  if (options.runType == UvRunType.SCRIPT) {
    val result = uv
      .isScriptSynced(!containsExact, Path.of(options.scriptOrModule))
      .onFailure {
        logger.warn(it.message)
      }
      .getOrNull()

    when (result) {
      ScriptSyncCheckResult.NoInlineMetadata -> hasNoMetadata = true
      ScriptSyncCheckResult.Synced -> {}
      ScriptSyncCheckResult.NotSynced -> {
        return Result.success(true)
      }
      null -> {
        return Result.failure(Unit)
      }
    }
  }

  if (hasNoMetadata && !options.uvArgs.contains("--no-project")) {
    val isProjectSynced = uv
      .isProjectSynced(!containsExact)
      .onFailure {
        logger.warn(it.message)
      }
      .getOrNull()

    when (isProjectSynced) {
      true -> {}
      false -> {
        return Result.success(true)
      }
      null -> {
        return Result.failure(Unit)
      }
    }
  }

  return Result.success(false)
}
