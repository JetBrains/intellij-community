/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author cdr
 */
package org.jetbrains.plugins.terminal.vfs;

import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.tabs.TabInfo;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.TabbedSettingsProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class TerminalSessionVirtualFileImpl extends LightVirtualFile {
  private final JediTermWidget myTerminal;
  private final TabbedSettingsProvider mySettingsProvider;

  private final TabInfo myTabInfo;

  public TerminalSessionVirtualFileImpl(@NotNull TabInfo tabInfo,
                                        @NotNull JediTermWidget terminal,
                                        @NotNull TabbedSettingsProvider settingsProvider) {
    myTabInfo = tabInfo;
    myTerminal = terminal;
    mySettingsProvider = settingsProvider;
    setFileType(TerminalSessionFileType.INSTANCE);
    setWritable(true);
  }

  public JediTermWidget getTerminal() {
    return myTerminal;
  }

  @NotNull
  public String getName() {
    return myTabInfo.getText();
  }

  public TabbedSettingsProvider getSettingsProvider() {
    return mySettingsProvider;
  }
}
