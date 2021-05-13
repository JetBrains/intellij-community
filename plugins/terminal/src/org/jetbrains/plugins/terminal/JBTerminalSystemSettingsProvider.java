// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase;
import com.intellij.terminal.JBTerminalWidget;
import com.jediterm.pty.PtyProcessTtyConnector;
import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.HyperlinkStyle;
import com.jediterm.terminal.TtyConnector;
import org.jetbrains.annotations.NotNull;

public class JBTerminalSystemSettingsProvider extends JBTerminalSystemSettingsProviderBase {
  @Override
  public boolean shouldCloseTabOnLogout(TtyConnector ttyConnector) {
    return TerminalOptionsProvider.getInstance().closeSessionOnLogout();
  }

  @Override
  public String tabName(TtyConnector ttyConnector, String sessionName) { //for local terminal use name from settings
    if (ttyConnector instanceof PtyProcessTtyConnector) {
      return TerminalOptionsProvider.getInstance().getTabName();
    }
    else {
      return sessionName;
    }
  }

  public @NlsSafe String getTabName(@NotNull JBTerminalWidget terminalWidget) {
    TtyConnector connector = terminalWidget.getTtyConnector();
    if (connector instanceof PtyProcessTtyConnector) {
      // use name from settings for local terminal
      return TerminalOptionsProvider.getInstance().getTabName();
    }
    return terminalWidget.getSessionName();
  }

  @Override
  public boolean audibleBell() {
    return TerminalOptionsProvider.getInstance().audibleBell();
  }

  @Override
  public boolean enableMouseReporting() {
    return TerminalOptionsProvider.getInstance().enableMouseReporting();
  }

  @Override
  public boolean copyOnSelect() {
    return TerminalOptionsProvider.getInstance().copyOnSelection();
  }

  @Override
  public boolean pasteOnMiddleMouseClick() {
    return TerminalOptionsProvider.getInstance().pasteOnMiddleMouseButton();
  }

  @Override
  public boolean forceActionOnMouseReporting() {
    return true;
  }

  @Override
  public boolean overrideIdeShortcuts() {
    return TerminalOptionsProvider.getInstance().overrideIdeShortcuts();
  }

  @Override
  public HyperlinkStyle.HighlightMode getHyperlinkHighlightingMode() {
    return TerminalOptionsProvider.getInstance().highlightHyperlinks()
           ? HyperlinkStyle.HighlightMode.ALWAYS
           : HyperlinkStyle.HighlightMode.HOVER;
  }

  @Override
  public @NotNull CursorShape getCursorShape() {
    TerminalOptionsProvider options = TerminalOptionsProvider.getInstance();
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    TerminalOptionsProvider.CursorShape shape = options.getCursorShape();
    if (shape == TerminalOptionsProvider.CursorShape.BLOCK) {
      return editorSettings.isBlinkCaret() ? CursorShape.BLINK_BLOCK : CursorShape.STEADY_BLOCK;
    }
    if (shape == TerminalOptionsProvider.CursorShape.UNDERLINE) {
      return editorSettings.isBlinkCaret() ? CursorShape.BLINK_UNDERLINE : CursorShape.STEADY_UNDERLINE;
    }
    return editorSettings.isBlinkCaret() ? CursorShape.BLINK_VERTICAL_BAR : CursorShape.STEADY_VERTICAL_BAR;
  }

  @Override
  public boolean altSendsEscape() {
    return TerminalOptionsProvider.getInstance().getUseOptionAsMetaKey();
  }
}
