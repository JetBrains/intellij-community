// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.errorProcessing

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.Result
import org.jetbrains.annotations.Nls

/**
 * Some "business" error: just a message to be displayed to a user
 */
open class MessageError(message: @NlsSafe String) : PyError(message)

fun failure(message: @Nls String): Result<Nothing, MessageError> = failure(MessageError(message))

suspend fun ErrorSink.emit(message: @Nls String) {
  emit(MessageError(message))
}