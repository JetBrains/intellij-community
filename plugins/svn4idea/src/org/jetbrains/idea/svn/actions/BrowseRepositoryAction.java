/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserDialog;

import javax.swing.*;
import java.awt.*;

public class BrowseRepositoryAction extends AnAction {

  public void actionPerformed(AnActionEvent e) {

    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    ToolWindowManager manager = ToolWindowManager.getInstance(project);


    ToolWindow w = manager.getToolWindow("SVN Repositories");
    if (w == null) {
      JComponent component = createToolWindowComponent(project);
      w = manager.registerToolWindow("SVN Repositories", component, ToolWindowAnchor.BOTTOM);
    }
    w.show(null);
    w.activate(null);
  }

  public void update(AnActionEvent e) {
    super.update(e);

    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    SvnVcs vcs = project != null ? SvnVcs.getInstance(project) : null;
    e.getPresentation().setEnabled(vcs != null);
  }

  private JComponent createToolWindowComponent(Project project) {
    RepositoryBrowserDialog dialog = new RepositoryBrowserDialog(project);
    JComponent component = dialog.createBrowserComponent(true);
    JPanel panel = new JPanel(new BorderLayout());

    panel.add(component, BorderLayout.CENTER);
    panel.add(dialog.createToolbar(false), BorderLayout.WEST);

    return panel;
  }
}
