/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.terminal.action;

import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory;
import org.jetbrains.plugins.terminal.TerminalView;

/**
 * An action that activates the terminal window for file, selected by user.
 */
public class RevealFileInTerminalAction extends DumbAwareAction {
  @Override
  public void update(AnActionEvent e) {
    Project project = getEventProject(e);
    e.getPresentation().setEnabledAndVisible(project != null && getSelectedFile(e) != null);
  }

  @Nullable
  private static VirtualFile getSelectedFile(@NotNull AnActionEvent e) {
    return ShowFilePathAction.findLocalFile(e.getData(CommonDataKeys.VIRTUAL_FILE));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = getEventProject(e);
    VirtualFile selectedFile = getSelectedFile(e);
    if (project == null || selectedFile == null) {
      return;
    }

    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
    if (window != null && window.isAvailable()) {
      TerminalView.getInstance(project).setFileToOpen(selectedFile);
      window.activate(null);
    }
  }
}
