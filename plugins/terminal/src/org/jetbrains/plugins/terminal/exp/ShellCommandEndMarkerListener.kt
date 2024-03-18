// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.terminal.TerminalUtil
import java.util.concurrent.atomic.AtomicBoolean

internal class ShellCommandEndMarkerListener(private val session: BlockTerminalSession, private val onFound: () -> Unit) {

  private val disposable: Disposable = Disposer.newDisposable(session, ShellCommandEndMarkerListener::class.java.simpleName)
  private val found: AtomicBoolean = AtomicBoolean(false)

  init {
    if (!findCommandEndMarker()) {
      TerminalUtil.addModelListener(session.model.textBuffer, disposable) {
        findCommandEndMarker()
      }
    }
  }

  private fun findCommandEndMarker(): Boolean {
    val output = ShellCommandOutputScraper.scrapeOutput(session)
    if (output.commandEndMarkerFound && found.compareAndSet(false, true)) {
      Disposer.dispose(disposable)
      onFound()
      return true
    }
    return false
  }
}
