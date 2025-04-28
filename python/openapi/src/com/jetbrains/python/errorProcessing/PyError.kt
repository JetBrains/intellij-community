// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.errorProcessing

import com.intellij.openapi.util.NlsSafe

/**
 * Error that is interested to user.
 * Such errors usually stem from user errors or external process errors (i.e., permissions, network connections).
 * Those are *not* NPEs nor OOBs nor various assertions.
 * Do *not* use `catch(Exception)` or `runCatching` with this class.
 *
 * Most probably you will send this error to [ErrorSink].
 */
sealed class PyError(val message: @NlsSafe String) {

  override fun toString(): String = message
}
