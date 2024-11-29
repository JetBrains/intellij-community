// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.vfs;

import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.TerminalIcons;

import javax.swing.*;

public final class TerminalSessionFileType extends FakeFileType {

  public final static TerminalSessionFileType INSTANCE = new TerminalSessionFileType();

  private TerminalSessionFileType() {
  }

  @Override
  @NotNull
  public String getName() {
    return "Terminal Session";
  }

  @Override
  @NotNull
  public String getDescription() {
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
