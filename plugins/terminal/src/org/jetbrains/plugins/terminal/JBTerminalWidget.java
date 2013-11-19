package org.jetbrains.plugins.terminal;

import com.intellij.ui.components.JBScrollBar;
import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.display.BackBuffer;
import com.jediterm.terminal.display.JediTerminal;
import com.jediterm.terminal.display.StyleState;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class JBTerminalWidget extends JediTermWidget {

  public JBTerminalWidget(JBTerminalSystemSettingsProvider settingsProvider) {
    super(settingsProvider);

    JBTabbedTerminalWidget.convertActions(this, getActions());
  }

  @Override
  protected JBTerminalPanel createTerminalPanel(@NotNull SettingsProvider settingsProvider,
                                                @NotNull StyleState styleState,
                                                @NotNull BackBuffer backBuffer) {
    return new JBTerminalPanel((JBTerminalSystemSettingsProvider)settingsProvider, backBuffer, styleState);
  }

  @Override
  protected TerminalStarter createTerminalStarter(JediTerminal terminal, TtyConnector connector) {
    return new JBTerminalStarter(terminal, connector);
  }

  @Override
  protected JScrollBar createScrollBar() {
    return new JBScrollBar();
  }
}
