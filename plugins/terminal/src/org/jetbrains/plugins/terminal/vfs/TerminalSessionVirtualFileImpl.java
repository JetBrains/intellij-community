// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.vfs;

import com.intellij.terminal.JBTerminalWidget;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.tabs.TabInfo;
import com.jediterm.terminal.ui.settings.TabbedSettingsProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class TerminalSessionVirtualFileImpl extends LightVirtualFile {
  private final JBTerminalWidget myTerminal;
  private final TabbedSettingsProvider mySettingsProvider;

  private final TabInfo myTabInfo;

  public TerminalSessionVirtualFileImpl(@NotNull TabInfo tabInfo,
                                        @NotNull JBTerminalWidget terminalWidget,
                                        @NotNull TabbedSettingsProvider settingsProvider) {
    myTabInfo = tabInfo;
    myTerminal = terminalWidget;
    mySettingsProvider = settingsProvider;
    setFileType(TerminalSessionFileType.INSTANCE);
    setWritable(true);
    terminalWidget.setVirtualFile(this);
  }

  public JBTerminalWidget getTerminalWidget() {
    return myTerminal;
  }

  @Override
  @NotNull
  public String getName() {
    return myTabInfo.getText();
  }

  public TabInfo getTabInfo() {
    return myTabInfo;
  }

  public TabbedSettingsProvider getSettingsProvider() {
    return mySettingsProvider;
  }
}
