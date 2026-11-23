// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.jetbrains.python.project.asPyProjectOrCandidate
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.sdk.ModuleOrProject
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.jetbrains.python.sdk.moduleIfExists
import com.jetbrains.python.sdk.add.collector.PythonNewInterpreterAddedCollector
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithTool
import com.jetbrains.python.sdk.withSdkConfigurationLock
import com.jetbrains.python.sdk.workingDirectory
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.file.Path

/**
 * When [okClicked] creates an interpreter, it sets it on the module and reports it to [interpreterSetFlow].
 * Only one latest interpreter is cached, hence must be collected before next [okClicked] which is used one time in production anyway
 *
 * @see PythonAddLocalInterpreterDialog
 */
class PythonAddLocalInterpreterPresenter(
  val moduleOrProject: ModuleOrProject,
  val envReader: VirtualEnvReader = VirtualEnvReader(),
  val errorSink: ErrorSink,
  val bestGuessCreateSdkInfo: Deferred<CreateSdkInfoWithTool?>,
) {

  /** The directory for a new environment: the working directory of [moduleOrProject], else the venv root of the user. */
  val pathForVEnv: Path
    get() = moduleOrProject.workingDirectory ?: envReader.getVEnvRootDir()

  private val _interpreterShared = MutableSharedFlow<PythonInterpreter>(1)
  val interpreterSetFlow: Flow<PythonInterpreter> = _interpreterShared.asSharedFlow()

  suspend fun okClicked(addEnvironment: PythonAddEnvironment<PathHolder.Eel>): Unit = withSdkConfigurationLock(moduleOrProject.project) {
    when (val r = addEnvironment.setupSdk(moduleOrProject)) {
      is Result.Failure -> {
        errorSink.emit(r.error, moduleOrProject.project)
        return@withSdkConfigurationLock
      }
      is Result.Success -> {
        // The interpreter the module then has: the registry can adopt an existing SDK for it.
        val set = moduleOrProject.moduleIfExists?.asPyProjectOrCandidate()?.setPythonInterpreter(r.result)?.getOr {
          errorSink.emit(it.error, moduleOrProject.project)
          return@withSdkConfigurationLock
        } ?: r.result
        val isPreviouslyConfigured = addEnvironment.createStatisticsInfo(PythonInterpreterCreationTargets.LOCAL_MACHINE).previouslyConfigured
        PythonNewInterpreterAddedCollector.logPythonNewInterpreterAdded(set, isPreviouslyConfigured)
        _interpreterShared.emit(set)
      }
    }
  }
}
