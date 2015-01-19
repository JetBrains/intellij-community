package org.jetbrains.plugins.terminal;

import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.jediterm.terminal.*;
import com.jediterm.terminal.emulator.JediEmulator;

import javax.swing.*;

/**
 * @author traff
 */
public class JBTerminalStarter extends TerminalStarter {
  public JBTerminalStarter(Terminal terminal, TtyConnector ttyConnector) {
    super(terminal, ttyConnector);
  }

  @Override
  protected JediEmulator createEmulator(TtyChannel channel, TerminalOutputStream stream, Terminal terminal) {
    return new JediEmulator(channel, stream, terminal) {
      @Override
      protected void unsupported(char... sequenceChars) {
        if (sequenceChars[0] == 7) { //ESC BEL
          refreshAfterExecution();
        }
        else {
          super.unsupported();
        }
      }
    };
  }

  public static void refreshAfterExecution() {
    if (GeneralSettings.getInstance().isSyncOnFrameActivation()) {
      //we need to refresh local file system after a command has been executed in the terminal
      LocalFileSystem.getInstance().refresh(true);
    }
  }
}
