// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.toNioPathOrNull
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.rootManager
import com.jetbrains.python.sdk.service.PySdkService.Companion.pySdkService
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.file.Path

/**
 * When [okClicked] creates sdk and reports it to [sdkCreatedFlow].
 * Only one latest SDK is cached, hence must be collected before next [okClicked] which is used one time in production anyway
 *
 * @see PythonAddLocalInterpreterDialog
 */
class PythonAddLocalInterpreterPresenter(val moduleOrProject: ModuleOrProject, val envReader: VirtualEnvReader = VirtualEnvReader.Instance, val errorSink: ErrorSink) {

  /**
   * Default path to create virtualenv it
   */
  val pathForVEnv: Path
    get() = when (moduleOrProject) {
              is ModuleOrProject.ModuleAndProject -> moduleOrProject.module.rootManager.contentRoots.firstOrNull()?.toNioPath()
                                                     ?: moduleOrProject.project.basePath?.toNioPathOrNull()
              is ModuleOrProject.ProjectOnly -> moduleOrProject.project.basePath?.toNioPathOrNull()
            } ?: envReader.getVEnvRootDir()

  private val _sdkShared = MutableSharedFlow<Sdk>(1)
  val sdkCreatedFlow: Flow<Sdk> = _sdkShared.asSharedFlow()

  suspend fun okClicked(addEnvironment: PythonAddEnvironment) {
    when (val r = addEnvironment.getOrCreateSdk(moduleOrProject)) {
      is Result.Failure -> {
        errorSink.emit(r.error)
        return
      }
      is Result.Success -> {
        moduleOrProject.project.pySdkService.persistSdk(r.result)
        _sdkShared.emit(r.result)
      }
    }
  }
}