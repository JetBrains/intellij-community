// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.run

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle
import com.jetbrains.python.Result
import com.jetbrains.python.getOrNull
import com.jetbrains.python.run.PythonCommandLineState
import com.jetbrains.python.run.PythonExecution
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import com.jetbrains.python.sdk.associatedModulePath
import com.jetbrains.python.sdk.uv.ScriptSyncCheckResult
import com.jetbrains.python.sdk.uv.impl.createUvCli
import com.jetbrains.python.sdk.uv.impl.createUvLowLevel
import com.jetbrains.python.sdk.uv.impl.getUvExecutable
import showSyncWarningDialogAndGet
import java.nio.file.Path

internal class UvRunConfigurationState(
  val uvRunConfiguration: UvRunConfiguration,
  env: ExecutionEnvironment,
  val project: Project,
) : PythonCommandLineState(uvRunConfiguration, env) {
  @RequiresEdt
  override fun canRun(): Boolean {
    // force save to make sure that commands reads the most up-to-date pyproject.toml
    FileDocumentManager.getInstance().saveAllDocuments()

    // if the check is disabled, then we can run the configuration immediately
    if (!uvRunConfiguration.options.checkSync) {
      return true
    }

    val associatedModulePath = uvRunConfiguration.options.uvSdk?.associatedModulePath
    val uvExecutable = getUvExecutable()
    var isError = false
    var isUnsynced = false

    if (associatedModulePath != null && uvExecutable != null) {
      runWithModalProgressBlocking(project, PyBundle.message("uv.run.configuration.state.progress.name")) {
        when (
          requiresSync(
            Path.of(associatedModulePath),
            uvExecutable,
            uvRunConfiguration.options
          ).getOrNull()
        ) {
          true -> isUnsynced = true
          false -> {}
          null -> isError = true
        }
      }
    } else {
      isError = true
    }

    if (isError || isUnsynced) {
      return showSyncWarningDialogAndGet(isError, uvRunConfiguration.options)
    }

    return true
  }

  override fun buildPythonExecution(helpersAwareRequest: HelpersAwareTargetEnvironmentRequest): PythonExecution {
    return buildUvRunConfigurationCli(uvRunConfiguration.options, isDebug)
  }
}

internal suspend fun requiresSync(modulePath: Path, uvExecutable: Path, options: UvRunConfigurationOptions): Result<Boolean, Unit> {
  // module scenarios:
  // 1. module with --no-project flag -- no sync
  // 2. module -- syncs project
  // script scenarios:
  // 1. script with no metadata -- syncs project
  // 2. script with no metadata and --no-project flag -- no sync
  // 3. script with metadata -- syncs script deps, doesn't sync project
  // 4. script with metadata and --no-project flag -- syncs script deps, doesn't sync project (flag is ignored)

  val uv = createUvLowLevel(modulePath, createUvCli(uvExecutable))
  val containsExact = !options.uvArgs.contains("--exact")
  var hasNoMetadata = options.runType == UvRunType.MODULE // modules have no script metadata, so it starts with false in case of a module

  if (options.runType == UvRunType.SCRIPT) {
    val result = uv
      .isScriptSynced(!containsExact, Path.of(options.scriptOrModule))
      .getOrNull()

    when (result) {
      ScriptSyncCheckResult.NoInlineMetadata -> hasNoMetadata = true
      ScriptSyncCheckResult.Synced -> {}
      ScriptSyncCheckResult.Unsynced -> {
        return Result.success(true)
      }
      null -> {
        return Result.failure(Unit)
      }
    }
  }

  if (hasNoMetadata && !options.uvArgs.contains("--no-project")) {
    val isProjectSynced = uv.isProjectSynced(!containsExact).getOrNull()

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