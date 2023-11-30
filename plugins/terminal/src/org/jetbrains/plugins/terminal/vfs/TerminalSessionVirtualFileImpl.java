// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.vfs;

import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.testFramework.LightVirtualFile;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class TerminalSessionVirtualFileImpl extends LightVirtualFile {
  private final TerminalWidget myTerminalWidget;
  private final SettingsProvider mySettingsProvider;

  public TerminalSessionVirtualFileImpl(@NotNull String name,
                                        @NotNull TerminalWidget terminalWidget,
                                        @NotNull SettingsProvider settingsProvider) {
    myTerminalWidget = terminalWidget;
    mySettingsProvider = settingsProvider;
    setFileType(TerminalSessionFileType.INSTANCE);
    setWritable(true);
    try {
      rename(null, name);
    }
    catch (IOException e) {
      throw new RuntimeException("Cannot rename");
    }
  }

  public @NotNull TerminalWidget getTerminalWidget() {
    return myTerminalWidget;
  }

  public @NotNull SettingsProvider getSettingsProvider() {
    return mySettingsProvider;
  }
}
