// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.util

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.PyBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.withContext

/**
 * [FlowCollector.emit] user-readable errors here.
 *
 * This class should be used by the topmost classes, tightly coupled to the UI.
 * For the most business-logic and backend functions please return [Result] or error.
 *
 * Please do not report *all* exceptions here: This is *not* the class for NPEs and AOOBs:
 * do not pass exceptions caught by `catch(e: Exception)` or `runCatching`: only report exceptions user interested in.
 * `IOException` or `ExecutionException` are generally ok.
 *
 * There will be unified sink soon to show and log errors.
 * Currently, only [ShowingMessageErrorSync] is a well-known implementation
 *
 * Example:
 * ```kotlin
 *  suspend fun someLogic(): Result<@NlsSafe String> = withContext(Dispatchers.IO) {
 *   try {
 *     Result.success(Path.of("1.txt").readText())
 *   }
 *   catch (e: IOException) {
 *     Result.failure(e)
 *   }
 * }
 *
 * suspend fun ui(errorSink: ErrorSink) {
 *   someLogic()
 *     .onSuccess {
 *       Messages.showInfoMessage("..", it)
 *     }
 *     .onFailure {
 *       errorSink.emit(it.localizedMessage)
 *     }
 * }
 * ```
 */
typealias ErrorSink = FlowCollector<@NlsSafe String>

/**
 * Displays error with a message box and writes it to a log.
 */
internal object ShowingMessageErrorSync : ErrorSink {
  override suspend fun emit(value: @NlsSafe String) {
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      thisLogger().warn(value)
      // Platform doesn't allow dialogs without lock for now, fix later
      writeIntentReadAction {
        Messages.showErrorDialog(value, PyBundle.message("python.error"))
      }
    }
  }
}
