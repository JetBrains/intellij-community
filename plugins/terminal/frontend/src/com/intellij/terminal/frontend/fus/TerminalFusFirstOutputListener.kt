package com.intellij.terminal.frontend.fus

import com.intellij.openapi.diagnostic.thisLogger
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo

internal class TerminalFusFirstOutputListener(private val startupFusInfo: TerminalStartupFusInfo) : TerminalOutputModelListener {
  /** Guarded by EDT */
  private var reported = false

  override fun afterContentChanged(model: TerminalOutputModel, startOffset: Int) {
    if (!reported && hasAnyMeaningfulText(model)) {
      reportFirstOutputReceived()
      reported = true
    }
  }

  private fun hasAnyMeaningfulText(model: TerminalOutputModel): Boolean {
    // Do not consider the '%' character as meaningful because Zsh can print and remove it several times on startup.
    return model.document.charsSequence.any { !it.isWhitespace() && it != '%' }
  }

  private fun reportFirstOutputReceived() {
    val latency = startupFusInfo.triggerTime.elapsedNow()
    ReworkedTerminalUsageCollector.logStartupFirstOutputLatency(startupFusInfo.way, latency)
    thisLogger().info("Reworked terminal startup first output latency: ${latency.inWholeMilliseconds} ms")
  }
}