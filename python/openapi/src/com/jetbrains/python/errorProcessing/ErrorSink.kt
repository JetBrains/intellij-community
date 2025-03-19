// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.errorProcessing

import kotlinx.coroutines.flow.FlowCollector

/**
 * [emit] user-readable errors here.
 *
 * This class should be used by the topmost classes, tightly coupled to the UI.
 * For the most business-logic and backend functions please return [com.jetbrains.python.Result] or error.
 *
 * Please do not report *all* exceptions here: This is *not* the class for NPEs and AOOBs:
 * do not pass exceptions caught by `catch(e: Exception)` or `runCatching`: only report exceptions user interested in.
 * `IOException` or `ExecutionException` are generally ok.
 *
 * There will be unified sink soon to show and log errors.
 * Currently, only [com.jetbrains.python.util.ShowingMessageErrorSync] is a well-known implementation
 *
 * Example:
 * ```kotlin
 *  suspend fun someLogic(): Result<@NlsSafe String, IOException> = withContext(Dispatchers.IO) {
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
typealias ErrorSink = FlowCollector<PyError>