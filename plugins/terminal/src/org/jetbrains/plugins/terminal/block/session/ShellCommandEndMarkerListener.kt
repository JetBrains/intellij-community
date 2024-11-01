// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.session

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.jediterm.terminal.model.TerminalTextBuffer
import org.jetbrains.plugins.terminal.block.session.TerminalModel.Companion.withLock
import org.jetbrains.plugins.terminal.block.session.scraper.CommandEndMarkerListeningStringCollector
import org.jetbrains.plugins.terminal.block.session.scraper.SimpleStringCollector
import org.jetbrains.plugins.terminal.block.session.scraper.SimpleTerminalLinesCollector
import org.jetbrains.plugins.terminal.block.session.scraper.StringCollector
import org.jetbrains.plugins.terminal.util.addModelListener
import java.util.concurrent.atomic.AtomicBoolean

internal class ShellCommandEndMarkerListener(
  private val terminalTextBuffer: TerminalTextBuffer,
  private val commandEndMarker: String?,
  parentDisposable: Disposable,
  private val onFound: () -> Unit,
) {

  private val disposable: Disposable = Disposer.newDisposable(parentDisposable, ShellCommandEndMarkerListener::class.java.simpleName)
  private val found: AtomicBoolean = AtomicBoolean(false)

  init {
    if (!findCommandEndMarker()) {
      terminalTextBuffer.addModelListener(disposable) {
        findCommandEndMarker()
      }
    }
  }

  private fun findCommandEndMarker(): Boolean {
    var commandEndMarkerFound = false
    terminalTextBuffer.withLock {
      val stringCollector: StringCollector = CommandEndMarkerListeningStringCollector(SimpleStringCollector(), commandEndMarker) {
        commandEndMarkerFound = true
      }
      // TODO no real need to collect all lines. Last lines of the screen must be enough.
      terminalTextBuffer.collectLines(SimpleTerminalLinesCollector(stringCollector))
    }
    if (commandEndMarkerFound && found.compareAndSet(false, true)) {
      Disposer.dispose(disposable)
      onFound()
      return true
    }
    return false
  }
}
