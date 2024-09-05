// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.terminal.TerminalStartupMoment
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession
import org.jetbrains.plugins.terminal.block.session.ShellCommandListener
import org.jetbrains.plugins.terminal.fus.TimeSpanType
import org.jetbrains.plugins.terminal.fus.TerminalUsageTriggerCollector
import org.jetbrains.plugins.terminal.util.ShellType
import java.time.Duration
import kotlin.time.toKotlinDuration

private class BlockTerminalStartupResponsivenessReporter(
  private val project: Project,
  private val startupMoment: TerminalStartupMoment,
  private val shellType: ShellType,
  parentDisposable: Disposable
) : ShellCommandListener {

  val disposable = Disposer.newDisposable(parentDisposable)

  // At this point, `TerminalCaretModel.onCommandRunningChanged(true)` should have been called already.
  // However, the terminal cursor is actually shown in 50ms after that, see `TerminalCaretModel.scheduleUpdate`.
  // Let's neglect these 50 ms for now. Maybe `TerminalCaretModel` could paint the cursor immediately on the first call?
  private val durationToCursorShownInInitializationBlock = startupMoment.elapsedNow()

  override fun initialized() {
    val durationToReadyPrompt = startupMoment.elapsedNow()
    val metrics = listOf(TimeSpanType.FROM_STARTUP_TO_SHOWN_CURSOR to durationToCursorShownInInitializationBlock,
                         TimeSpanType.FROM_STARTUP_TO_READY_PROMPT to durationToReadyPrompt)
    thisLogger().info("${shellType} block terminal started fully (" + metrics.joinToString { formatMessage(it.first, it.second) } + ")")
    metrics.forEach {
      TerminalUsageTriggerCollector.logBlockTerminalTimeSpanFinished(project, shellType, it.first, it.second.toKotlinDuration())
    }
    Disposer.dispose(disposable)
  }
}

private fun formatMessage(timeSpanType: TimeSpanType, duration: Duration): String {
  return "${timeSpanType.description}: ${duration.toMillis()} ms"
}

internal fun installStartupResponsivenessReporter(project: Project, startupMoment: TerminalStartupMoment, session: BlockTerminalSession) {
  val reporter = BlockTerminalStartupResponsivenessReporter(project, startupMoment, session.shellIntegration.shellType, session)
  session.addCommandListener(reporter, reporter.disposable)
}
