package com.intellij.terminal.backend

import com.jediterm.core.typeahead.TerminalTypeAheadManager
import com.jediterm.terminal.*
import com.jediterm.terminal.emulator.JediEmulator
import com.jediterm.terminal.model.JediTerminal
import org.jetbrains.plugins.terminal.fus.BackendOutputActivity

internal class StopAwareTerminalStarter(
  terminal: JediTerminal,
  ttyConnector: TtyConnector,
  dataStream: TerminalDataStream,
  typeAheadManager: TerminalTypeAheadManager,
  executorServiceManager: TerminalExecutorServiceManager,
  private val fusActivity: BackendOutputActivity,
) : TerminalStarter(terminal, ttyConnector, dataStream, typeAheadManager, executorServiceManager) {
  @Volatile
  var isStopped: Boolean = false
    private set

  override fun createEmulator(dataStream: TerminalDataStream, terminal: Terminal): JediEmulator {
    return FusAwareEmulator(dataStream, terminal)
  }

  override fun requestEmulatorStop() {
    super.requestEmulatorStop()
    isStopped = true
  }

  // must be an inner class, because this thing is created in a superclass constructor,
  // so fusActivity is still null at that time, and we can't pass its actual value
  private inner class FusAwareEmulator(
    dataStream: TerminalDataStream,
    terminal: Terminal,
  ) : JediEmulator(dataStream, terminal) {
    override fun next() {
      fusActivity.charProcessingStarted()
      super.next()
      fusActivity.charProcessingFinished()
    }
  }
}
