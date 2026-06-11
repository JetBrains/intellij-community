package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.completion.escapeShellArgument
import org.jetbrains.plugins.terminal.session.ShellName
import org.jetbrains.plugins.terminal.session.guessShellName
import org.jetbrains.plugins.terminal.startup.TerminalLocalPathTranslator
import org.jetbrains.plugins.terminal.util.getNow
import java.nio.file.Path

internal object TerminalFilePathHandler {
  fun getPathAsText(paths: List<Path>, context: TerminalProcessContext): String {
    return paths.joinToString(" ") { formatPath(it, context) }
  }

  fun formatPath(nioPath: Path, context: TerminalProcessContext): String {
    val pathInMonolith = getPathInMonolith(nioPath, context.eelDescriptor)
    return escapeShellArgument(pathInMonolith, context.shellName)
  }

  private fun getPathInMonolith(nioPath: Path, eelDescriptor: EelDescriptor): String =
    TerminalLocalPathTranslator(eelDescriptor).translateAbsoluteLocalPathToRemote(nioPath)?.toString() ?: nioPath.asEelPath().toString()
}

internal data class TerminalProcessContext(
  val eelDescriptor: EelDescriptor,
  val shellName: ShellName,
)

internal fun getTerminalContext(terminalView: TerminalView): TerminalProcessContext? {
  val eelDescriptor = terminalView.sessionDeferred.getNow()?.eelDescriptor ?: return null
  val shellName = terminalView.startupOptionsDeferred.getNow()?.guessShellName() ?: ShellName.of("unknown")
  return TerminalProcessContext(eelDescriptor, shellName)
}