// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.execution.ExecutionException
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

/**
 * The exception implements [ControlFlowException] to skip the logging (see `org.jetbrains.concurrency.Promises.errorIfNotMessage`).
 */
@ApiStatus.Internal
internal class ExecutionExceptionWithHyperlink(s: @NlsContexts.DialogMessage String, private val hyperlinkActivatedCallback: Runnable)
  : ExecutionException(s), HyperlinkListener, ControlFlowException {
  override fun hyperlinkUpdate(e: HyperlinkEvent) {
    if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
      hyperlinkActivatedCallback.run()
    }
  }
}

/**
 * The exception implements [ControlFlowException] to skip the logging (see `org.jetbrains.concurrency.Promises.errorIfNotMessage`).
 */
@ApiStatus.Internal
internal class RuntimeExceptionWithHyperlink(s: @NlsContexts.DialogMessage String, private val hyperlinkActivatedCallback: Runnable)
  : RuntimeException(s), HyperlinkListener, ControlFlowException {
  override fun hyperlinkUpdate(e: HyperlinkEvent) {
    if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
      hyperlinkActivatedCallback.run()
    }
  }
}