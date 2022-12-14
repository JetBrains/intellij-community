// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.intellij.terminal.TerminalTitle;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.terminal.ui.TtyConnectorAccessor;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.WinSize;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class MyTerminalWidget implements TerminalWidget {

  private final TerminalTitle myTerminalTitle = new TerminalTitle();

  private final JLabel label = new JLabel("Hello");

  @Override
  public @NotNull JComponent getComponent() {
    return label;
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return label;
  }

  @NotNull
  @Override
  public TerminalTitle getTerminalTitle() {
    return myTerminalTitle;
  }

  @NotNull
  @Override
  public WinSize getWindowSize() {
    return new WinSize(80, 24);
  }

  @Override
  public void connectToTty(@NotNull TtyConnector ttyConnector) {

  }

  private final TtyConnectorAccessor myTtyConnectorAccessor = new TtyConnectorAccessor();
  @NotNull
  @Override
  public TtyConnectorAccessor getTtyConnectorAccessor() {
    return myTtyConnectorAccessor;
  }

  @Override
  public void writePlainMessage(@NotNull @Nls String message) {

  }

  @Override
  public void setCursorVisible(boolean visible) {

  }

  @Override
  public boolean hasFocus() {
    return false;
  }

  @Override
  public void requestFocus() {

  }

  @Override
  public void addTerminationCallback(@NotNull Runnable onTerminated) {

  }

  @Override
  public void removeTerminationCallback(@NotNull Runnable onTerminated) {

  }

  @Override
  public void dispose() {

  }
}
