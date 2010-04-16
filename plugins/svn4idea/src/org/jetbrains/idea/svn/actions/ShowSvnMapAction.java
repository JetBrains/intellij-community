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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ContentsUtil;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.dialogs.CopiesPanel;

public class ShowSvnMapAction extends AnAction implements DumbAware {
  public ShowSvnMapAction() {
    super(SvnBundle.message("action.show.svn.map.text"));
  }

  @Override
  public void update(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);

    final Presentation presentation = e.getPresentation();
    presentation.setVisible(project != null);
    presentation.setEnabled(project != null);

    //presentation.setText(SvnBundle.message("action.show.svn.map.text"));
    presentation.setDescription(SvnBundle.message("action.show.svn.map.description"));
    presentation.setIcon(IconLoader.getIcon("/icons/ShowWorkingCopies.png"));
  }

  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      return;
    }

    final CopiesPanel copiesPanel = new CopiesPanel(project);
    //final SvnMapDialog dialog = new SvnMapDialog(project);
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS);
    final ContentManager contentManager = toolWindow.getContentManager();

    Content content = ContentFactory.SERVICE.getInstance().createContent(copiesPanel.getComponent(), SvnBundle.message("dialog.show.svn.map.title"), true);
    ContentsUtil.addOrReplaceContent(contentManager, content, true);
    toolWindow.activate(new Runnable() {
      public void run() {
        IdeFocusManager.getInstance(project).requestFocus(copiesPanel.getPrefferedFocusComponent(), true);
      }
    });
    /*SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        IdeFocusManager.getInstance(project).requestFocus(copiesPanel.getPrefferedFocusComponent(), true);
      }
    });*/
  }
}
