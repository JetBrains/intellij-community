// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.EDT
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.toNioPathOrNull
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.rootManager
import com.jetbrains.python.sdk.service.PySdkService.Companion.pySdkService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * When [okClicked] creates sdk and reports it to [sdkCreatedFlow].
 * Only one latest SDK is cached, hence must be collected before next [okClicked] which is used one time in production anyway
 *
 * @see PythonAddLocalInterpreterDialog
 */
class PythonAddLocalInterpreterPresenter(val moduleOrProject: ModuleOrProject) {

  /**
   * Default path to create virtualenv it
   */
  val pathForVEnv: Path
    get() = when (moduleOrProject) {
              is ModuleOrProject.ModuleAndProject -> moduleOrProject.module.rootManager.contentRoots.firstOrNull()?.toNioPath()
              is ModuleOrProject.ProjectOnly -> moduleOrProject.project.basePath?.toNioPathOrNull()
            } ?: Path.of(System.getProperty("user.home"))

  private val _sdkShared = MutableSharedFlow<Sdk>(1)
  val sdkCreatedFlow: Flow<Sdk> = _sdkShared.asSharedFlow()

  suspend fun okClicked(addEnvironment: PythonAddEnvironment) {
    val sdk = withContext(Dispatchers.EDT) { addEnvironment.getOrCreateSdk() }
    moduleOrProject.project.pySdkService.persistSdk(sdk)
    _sdkShared.emit(sdk)
  }
}