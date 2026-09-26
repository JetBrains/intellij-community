// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.errorProcessing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.FlowCollector

/**
 * [emit] user-readable [PyError] errors here.
 *
 * This class should be used by the topmost classes, tightly coupled to the UI.
 * For the most business-logic and backend functions please return [PyResult] or [PyError].
 *
 * There will be a unified sink soon to show and log errors.
 * Use function of the same name to get an instance, or accept it as an argument as it can be mocked in tests.
 *
 * See [PyError]
 */
fun interface ErrorSink : FlowCollector<PyErrorDetail>

/**
 * Default implementation of [ErrorSink] that shows message to user, but try to accept [ErrorSink] as an argument, use
 * this function as a default value as a last resort only.
 */
fun ErrorSink(): ErrorSink = ApplicationManager.getApplication().service<ErrorSink>()

data class PyErrorDetail(
  val error: PyError,
  val project: Project? = null,
)

suspend fun ErrorSink.emit(error: PyError, project: Project? = null) {
  emit(PyErrorDetail(error, project))
}

fun ErrorSink.withProject(project: Project): ErrorSink = ErrorSink {
  emit(PyErrorDetail(it.error, project))
}