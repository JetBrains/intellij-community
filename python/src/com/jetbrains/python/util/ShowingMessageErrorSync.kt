// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.Messages
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.ExecError
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyErrorDetail
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.showProcessExecutionErrorDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Displays the error with a message box and writes it to a log.
 */
@ApiStatus.Internal
object ShowingMessageErrorSync : ErrorSink {
  override suspend fun emit(value: PyErrorDetail) {
    val (error, project) = value

    // In unit tests dialogs are not supported
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw PyExecutionException(error)
    }

    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      thisLogger().warn(error.message)
      // Platform doesn't allow dialogs without a lock for now, fix later
      writeIntentReadAction {
        when (val e = error) {
          is ExecError -> {
            showProcessExecutionErrorDialog(project, e)
          }
          is MessageError -> {
            Messages.showErrorDialog(error.message, PyBundle.message("python.error"))
          }
        }
      }
    }
  }
}