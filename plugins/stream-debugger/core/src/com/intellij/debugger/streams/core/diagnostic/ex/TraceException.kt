// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.diagnostic.ex

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import org.jetbrains.annotations.ApiStatus

/**
 * @author Vitaliy.Bibaev
 */
@ApiStatus.Internal
abstract class TraceException internal constructor(message: String, traceExpression: String, cause: Throwable? = null) :
    RuntimeExceptionWithAttachments(message, cause, Attachment("trace.txt", traceExpression))
