package org.jetbrains.plugins.terminal;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.impl.IdeBackgroundUtil;
import com.intellij.ui.components.JBScrollBar;
import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class JBTerminalWidget extends JediTermWidget implements Disposable{

  public JBTerminalWidget(JBTerminalSystemSettingsProvider settingsProvider, Disposable parent) {
    super(settingsProvider);

    JBTabbedTerminalWidget.convertActions(this, getActions());

    Disposer.register(parent, this);
  }

  @Override
  protected JBTerminalPanel createTerminalPanel(@NotNull SettingsProvider settingsProvider,
                                                @NotNull StyleState styleState,
                                                @NotNull TerminalTextBuffer textBuffer) {
    JBTerminalPanel panel = new JBTerminalPanel((JBTerminalSystemSettingsProvider)settingsProvider, textBuffer, styleState);
    Disposer.register(this, panel);
    return panel;
  }

  @Override
  public void paint(Graphics g) {
    super.paint(IdeBackgroundUtil.withEditorBackground(g, this));
  }

  @Override
  protected TerminalStarter createTerminalStarter(JediTerminal terminal, TtyConnector connector) {
    return new JBTerminalStarter(terminal, connector);
  }

  @Override
  protected JScrollBar createScrollBar() {
    return new JBScrollBar();
  }

  @Override
  public void dispose() {
  }
}
