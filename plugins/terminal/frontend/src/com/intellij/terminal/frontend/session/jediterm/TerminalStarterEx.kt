package com.intellij.terminal.frontend.session.jediterm

import com.jediterm.core.typeahead.TerminalTypeAheadManager
import com.jediterm.terminal.TerminalDataStream
import com.jediterm.terminal.TerminalExecutorServiceManager
import com.jediterm.terminal.TerminalStarter
import com.jediterm.terminal.TtyConnector
import com.jediterm.terminal.model.JediTerminal

internal class TerminalStarterEx(
  terminal: JediTerminal,
  ttyConnector: TtyConnector,
  dataStream: TerminalDataStream,
  typeAheadManager: TerminalTypeAheadManager,
  executorServiceManager: TerminalExecutorServiceManager,
) : TerminalStarter(terminal, ttyConnector, dataStream, typeAheadManager, executorServiceManager) {
  @Volatile
  var isStopped: Boolean = false
    private set

  override fun requestEmulatorStop() {
    super.requestEmulatorStop()
    isStopped = true
  }
}
