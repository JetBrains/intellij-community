/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
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
    Project project = e.getData(CommonDataKeys.PROJECT);
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
        w.setHelpId("reference.svn.repository");
        final Content content = ContentFactory.SERVICE.getInstance().createContent(component, "", false);
        Disposer.register(content, component);
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
      add(myDialog.createToolbar(false), BorderLayout.WEST);
    }

    public void dispose() {
      myDialog.disposeRepositoryBrowser();
      ToolWindowManager.getInstance(myProject).unregisterToolWindow(REPOSITORY_BROWSER_TOOLWINDOW);
    }
  }
}
