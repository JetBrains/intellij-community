package com.intellij.terminal.frontend.fus

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.terminal.frontend.view.impl.TerminalCursorPainterListener
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import org.jetbrains.plugins.terminal.fus.TerminalTabOpeningWay
import kotlin.time.TimeMark

internal class TerminalFusCursorPainterListener(
  private val triggerTime: TimeMark,
  private val openingWay: TerminalTabOpeningWay,
) : TerminalCursorPainterListener {
  /** Guarded by EDT */
  private var reported = false

  override fun cursorPainted() {
    if (!reported) {
      reportCursorPainted()
      reported = true
    }
  }

  private fun reportCursorPainted() {
    val latency = triggerTime.elapsedNow()
    ReworkedTerminalUsageCollector.logStartupCursorShowingLatency(openingWay, latency)
    thisLogger().info("Reworked terminal startup cursor showing latency: ${latency.inWholeMilliseconds} ms")
  }
}