/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.CommonBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserDialog;

import javax.swing.*;
import java.awt.*;

public class BrowseRepositoryAction extends AnAction implements DumbAware {
  public static final String REPOSITORY_BROWSER_TOOLWINDOW = "SVN Repositories";

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      RepositoryBrowserDialog dialog = new RepositoryBrowserDialog(ProjectManager.getInstance().getDefaultProject());
      dialog.show();
    }
    else {
      ToolWindowManager manager = ToolWindowManager.getInstance(project);
      ToolWindow w = manager.getToolWindow(REPOSITORY_BROWSER_TOOLWINDOW);
      if (w == null) {
        RepositoryToolWindowPanel component = new RepositoryToolWindowPanel(project);
        w = manager.registerToolWindow(REPOSITORY_BROWSER_TOOLWINDOW, true, ToolWindowAnchor.BOTTOM, project, true);
        final Content content = ContentFactory.SERVICE.getInstance().createContent(component, "", false);
        content.setDisposer(component);
        w.getContentManager().addContent(content);
      }
      w.show(null);
      w.activate(null);
    }
  }

  private static class RepositoryToolWindowPanel extends JPanel implements Disposable {
    private final RepositoryBrowserDialog myDialog;
    private final Project myProject;

    private RepositoryToolWindowPanel(final Project project) {
      super(new BorderLayout());
      myProject = project;

      myDialog = new RepositoryBrowserDialog(project);
      JComponent component = myDialog.createBrowserComponent(true);

      add(component, BorderLayout.CENTER);
      add(myDialog.createToolbar(false, new HelpAction()), BorderLayout.WEST);
    }

    public void dispose() {
      myDialog.disposeRepositoryBrowser();
      ToolWindowManager.getInstance(myProject).unregisterToolWindow(BrowseRepositoryAction.REPOSITORY_BROWSER_TOOLWINDOW);
    }

    private class HelpAction extends AnAction {
      public HelpAction() {
        super(CommonBundle.getHelpButtonText(), null, IconLoader.getIcon("/actions/help.png"));
      }

      public void actionPerformed(AnActionEvent e) {
        HelpManager.getInstance().invokeHelp("reference.svn.repository");
      }
    }
  }
}
