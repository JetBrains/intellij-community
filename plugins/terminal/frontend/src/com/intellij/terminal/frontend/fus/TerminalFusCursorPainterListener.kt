package com.intellij.terminal.frontend.fus

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.terminal.frontend.TerminalCursorPainterListener
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo

internal class TerminalFusCursorPainterListener(private val startupFusInfo: TerminalStartupFusInfo) : TerminalCursorPainterListener {
  /** Guarded by EDT */
  private var reported = false

  override fun cursorPainted() {
    if (!reported) {
      reportCursorPainted()
      reported = true
    }
  }

  private fun reportCursorPainted() {
    val latency = startupFusInfo.triggerTime.elapsedNow()
    ReworkedTerminalUsageCollector.logStartupCursorShowingLatency(startupFusInfo.way, latency)
    thisLogger().info("Reworked terminal startup cursor showing latency: ${latency.inWholeMilliseconds} ms")
  }
}