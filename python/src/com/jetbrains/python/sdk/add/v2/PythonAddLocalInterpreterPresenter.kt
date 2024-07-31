// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
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
class PythonAddLocalInterpreterPresenter(internal val project: Project) {

  val basePathForEnv: Path get() = Path.of(project.basePath ?: (System.getProperty("user.home")))

  private val _sdkShared = MutableSharedFlow<Sdk>(1)
  val sdkCreatedFlow: Flow<Sdk> = _sdkShared.asSharedFlow()

  suspend fun okClicked(addEnvironment: PythonAddEnvironment) {
    val sdk = withContext(Dispatchers.EDT) { addEnvironment.getOrCreateSdk() }
    project.pySdkService.persistSdk(sdk)
    _sdkShared.emit(sdk)
  }
}