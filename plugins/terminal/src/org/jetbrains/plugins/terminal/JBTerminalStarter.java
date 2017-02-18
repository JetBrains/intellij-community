package org.jetbrains.plugins.terminal;

import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.TerminalDataStream;
import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.emulator.JediEmulator;

/**
 * @author traff
 */
public class JBTerminalStarter extends TerminalStarter {

  public JBTerminalStarter(Terminal terminal, TtyConnector ttyConnector, TerminalDataStream dataStream) {
    super(terminal, ttyConnector, dataStream);
  }

  @Override
  protected JediEmulator createEmulator(TerminalDataStream dataStream, Terminal terminal) {
    return new JediEmulator(dataStream, terminal) {
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
