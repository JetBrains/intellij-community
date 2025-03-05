// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.vfs;

import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.TerminalIcons;

import javax.swing.*;

public final class TerminalSessionFileType extends FakeFileType {

  public static final TerminalSessionFileType INSTANCE = new TerminalSessionFileType();

  private TerminalSessionFileType() {
  }

  @Override
  public @NotNull String getName() {
    return "Terminal Session";
  }

  @Override
  public @NotNull String getDescription() {
    return getName() + " Fake File Type"; //NON-NLS
  }

  @Override
  public Icon getIcon() {
    return TerminalIcons.OpenTerminal_13x13;
  }

  @Override
  public boolean isMyFileType(@NotNull VirtualFile file) {
    return file instanceof TerminalSessionVirtualFileImpl;
  }
}
