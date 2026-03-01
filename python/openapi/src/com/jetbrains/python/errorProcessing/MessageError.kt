// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.errorProcessing

import org.jetbrains.annotations.Nls

/**
 * Some "business" error: just a message to be displayed to a user
 */
open class MessageError(message: @Nls String) : PyError(message)

suspend fun ErrorSink.emit(message: @Nls String) {
  emit(MessageError(message))
}