// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.view;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.TerminalBundle;
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;

/**
 * @see org.jetbrains.plugins.terminal.action.RevealFileInTerminalAction
 */
public class SelectInTerminal implements SelectInTarget {

  @Override
  public String toString() {
    return TerminalBundle.message("toolwindow.stripe.Terminal");
  }

  @Override
  public boolean canSelect(@NotNull SelectInContext context) {
    return RevealFileAction.findLocalFile(context.getVirtualFile()) != null;
  }

  @Override
  public void selectIn(SelectInContext context, boolean requestFocus) {
    Project project = context.getProject();
    VirtualFile selectedFile = context.getVirtualFile();
    TerminalToolWindowManager.getInstance(project).openTerminalIn(selectedFile);
  }

  @Override
  public @Nullable String getToolWindowId() {
    return TerminalToolWindowFactory.TOOL_WINDOW_ID;
  }

  /**
   * @see com.intellij.ide.StandardTargetWeights
   */
  @Override
  public float getWeight() {
    return 9.7f;
  }
}
