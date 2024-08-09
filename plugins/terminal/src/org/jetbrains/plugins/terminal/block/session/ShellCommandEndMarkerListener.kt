// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.session

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.jediterm.terminal.model.TerminalTextBuffer
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.block.session.TerminalModel.Companion.withLock
import org.jetbrains.plugins.terminal.block.session.scraper.CommandEndMarkerListeningStringCollector
import org.jetbrains.plugins.terminal.block.session.scraper.SimpleStringCollector
import org.jetbrains.plugins.terminal.block.session.scraper.SimpleTerminalLinesCollector
import org.jetbrains.plugins.terminal.block.session.scraper.StringCollector
import java.util.concurrent.atomic.AtomicBoolean

internal class ShellCommandEndMarkerListener(
  private val terminalTextBuffer: TerminalTextBuffer,
  private val commandEndMarker: String?,
  parentDisposable: Disposable,
  private val onFound: () -> Unit,
) {

  @Deprecated(
    "Does not really requires session",
    replaceWith = ReplaceWith(
      "ShellCommandEndMarkerListener(session.model.textBuffer, session.commandBlockIntegration.commandEndMarker, session as Disposable, onFound)"
    )
  )
  constructor(
    session: BlockTerminalSession,
    onFound: () -> Unit,
  ) : this(
    session.model.textBuffer,
    session.commandBlockIntegration.commandEndMarker,
    session as Disposable,
    onFound
  )

  private val disposable: Disposable = Disposer.newDisposable(parentDisposable, ShellCommandEndMarkerListener::class.java.simpleName)
  private val found: AtomicBoolean = AtomicBoolean(false)

  init {
    if (!findCommandEndMarker()) {
      TerminalUtil.addModelListener(terminalTextBuffer, disposable) {
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
