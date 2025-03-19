// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.intellij.openapi.util.NlsContexts
import com.jetbrains.python.execution.FailureReason
import com.jetbrains.python.execution.PyExecutionFailure
import com.jetbrains.python.execution.userMessage

internal data class PyExecFailureImpl(
  override val command: String,
  override val args: List<String>,
  override val additionalMessage: @NlsContexts.DialogTitle String? = null,
  override val failureReason: FailureReason,
) : PyExecutionFailure {
  override fun toString(): String = userMessage
}