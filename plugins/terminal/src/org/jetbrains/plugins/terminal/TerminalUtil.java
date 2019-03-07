// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.terminal.JBTerminalWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TerminalUtil {
  private TerminalUtil() {}

  @NotNull
  public static JBTerminalWidget createTerminal(@NotNull AbstractTerminalRunner terminalRunner,
                                                @Nullable TerminalTabState tabState,
                                                @Nullable Disposable parentDisposable) {
    VirtualFile currentWorkingDir = getCurrentWorkingDir(tabState);
    if (parentDisposable == null) {
      parentDisposable = Disposer.newDisposable();
    }
    return terminalRunner.createTerminalWidget(parentDisposable, currentWorkingDir);
  }

  @Nullable
  private static VirtualFile getCurrentWorkingDir(@Nullable TerminalTabState tabState) {
    String dir = tabState != null ? tabState.myWorkingDirectory : null;
    VirtualFile result = null;
    if (dir != null) {
      result = LocalFileSystem.getInstance().findFileByPath(dir);
    }
    return result;
  }

}
