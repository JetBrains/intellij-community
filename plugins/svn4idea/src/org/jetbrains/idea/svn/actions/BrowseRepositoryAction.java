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
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.Disposable;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserDialog;

import javax.swing.*;
import java.awt.*;

public class BrowseRepositoryAction extends AnAction {
  public static final String REPOSITORY_BROWSER_TOOLWINDOW = "SVN Repositories";

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    if (project == null) {
      RepositoryBrowserDialog dialog = new RepositoryBrowserDialog(ProjectManager.getInstance().getDefaultProject());
      dialog.show();
    }
    else {
      ToolWindowManager manager = ToolWindowManager.getInstance(project);


      ToolWindow w = manager.getToolWindow(REPOSITORY_BROWSER_TOOLWINDOW);
      if (w == null) {
        RepositoryToolWindowPanel component = new RepositoryToolWindowPanel(project);
        w = manager.registerToolWindow(REPOSITORY_BROWSER_TOOLWINDOW, true, ToolWindowAnchor.BOTTOM);
        final Content content = PeerFactory.getInstance().getContentFactory().createContent(component, "", false);
        content.setDisposer(component);
        w.getContentManager().addContent(content);
      }
      w.show(null);
      w.activate(null);
    }
  }

  private static class RepositoryToolWindowPanel extends JPanel implements Disposable {
    private RepositoryBrowserDialog myDialog;

    private RepositoryToolWindowPanel(Project project) {
      super(new BorderLayout());

      myDialog = new RepositoryBrowserDialog(project);
      JComponent component = myDialog.createBrowserComponent(true);

      add(component, BorderLayout.CENTER);
      add(myDialog.createToolbar(false), BorderLayout.WEST);
    }

    public void dispose() {
      myDialog.disposeRepositoryBrowser();
    }
  }
}
