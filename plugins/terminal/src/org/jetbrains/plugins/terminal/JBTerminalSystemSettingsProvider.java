// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase;
import com.intellij.terminal.JBTerminalWidget;
import com.jediterm.pty.PtyProcessTtyConnector;
import com.jediterm.terminal.HyperlinkStyle;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.TerminalTypeAheadSettings;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class JBTerminalSystemSettingsProvider extends JBTerminalSystemSettingsProviderBase {
  @Override
  public boolean shouldCloseTabOnLogout(TtyConnector ttyConnector) {
    return TerminalOptionsProvider.getInstance().getCloseSessionOnLogout();
  }

  /**
   * @deprecated use {@link #getTabName(JBTerminalWidget)} instead
   */
  @Deprecated
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
    return TerminalOptionsProvider.getInstance().getAudibleBell();
  }

  @Override
  public boolean enableMouseReporting() {
    return TerminalOptionsProvider.getInstance().getMouseReporting();
  }

  @Override
  public boolean copyOnSelect() {
    return TerminalOptionsProvider.getInstance().getCopyOnSelection();
  }

  @Override
  public boolean pasteOnMiddleMouseClick() {
    return TerminalOptionsProvider.getInstance().getPasteOnMiddleMouseButton();
  }

  @Override
  public boolean forceActionOnMouseReporting() {
    return true;
  }

  @Override
  public boolean overrideIdeShortcuts() {
    return TerminalOptionsProvider.getInstance().getOverrideIdeShortcuts();
  }

  @Override
  public HyperlinkStyle.HighlightMode getHyperlinkHighlightingMode() {
    return TerminalOptionsProvider.getInstance().getHighlightHyperlinks()
           ? HyperlinkStyle.HighlightMode.ALWAYS
           : HyperlinkStyle.HighlightMode.HOVER;
  }

  @Override
  public boolean altSendsEscape() {
    return !SystemInfo.isMac || TerminalOptionsProvider.getInstance().getUseOptionAsMetaKey();
  }

  @Override
  public @NotNull TerminalTypeAheadSettings getTypeAheadSettings() {
    return new TerminalTypeAheadSettings(
      AdvancedSettings.getBoolean("terminal.type.ahead"),
      TimeUnit.MILLISECONDS.toNanos(AdvancedSettings.getInt("terminal.type.ahead.latency.threshold")),
      TerminalTypeAheadSettings.DEFAULT.getTypeAheadStyle()
    );
  }
}
