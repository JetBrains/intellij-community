package com.intellij.terminal.backend

import com.intellij.openapi.project.Project
import fleet.multiplatform.shims.ConcurrentHashMap
import org.jetbrains.plugins.terminal.block.reworked.TerminalShellIntegrationEventsListener
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import kotlin.time.Duration.Companion.milliseconds

internal class TerminalShellIntegrationStatisticsListener(private val project: Project) : TerminalShellIntegrationEventsListener {

  private val commandStartTimes = ConcurrentHashMap<String, Long>()

  override fun commandStarted(command: String) {
    commandStartTimes[command] = System.currentTimeMillis()
    ReworkedTerminalUsageCollector.logCommandStarted(project, command)
  }

  override fun commandFinished(command: String, exitCode: Int, currentDirectory: String) {
    val now = System.currentTimeMillis()
    var duration = 0L
    // All this defensive coding is likely unnecessary, as this thing is probably invoked in a single thread,
    // so a single "last command start time" variable would be enough.
    // But it's not hard to do and could be useful someday later because who knows what can change and where.
    val started = commandStartTimes.remove(command)
    if (started != null) {
      duration = now - started
    }
    ReworkedTerminalUsageCollector.logCommandFinished(project, command, exitCode, duration.milliseconds)
  }
}
