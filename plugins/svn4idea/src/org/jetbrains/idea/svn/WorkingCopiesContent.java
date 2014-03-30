/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.svn;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentI;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.dialogs.CopiesPanel;

public class WorkingCopiesContent {
  private final SvnVcs myVcs;
  private Content myShownContent;

  public WorkingCopiesContent(SvnVcs vcs) {
    myVcs = vcs;
  }

  public void activate() {
    final ChangesViewContentI cvcm = ChangesViewContentManager.getInstance(myVcs.getProject());
    final ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();

    final CopiesPanel copiesPanel = new CopiesPanel(myVcs.getProject());
    myShownContent = contentFactory.createContent(copiesPanel.getComponent(), SvnBundle.message("dialog.show.svn.map.title"), true);
    myShownContent.setCloseable(false);
    cvcm.addContent(myShownContent);
    myShownContent.setPreferredFocusableComponent(copiesPanel.getPreferredFocusedComponent());
  }

  public void deactivate() {
    if (myShownContent != null) {
      final ChangesViewContentI cvcm = ChangesViewContentManager.getInstance(myVcs.getProject());
      cvcm.removeContent(myShownContent);
      myShownContent = null;
    }
  }

  public static void show(@NotNull Project project) {
    final ToolWindowManager manager = ToolWindowManager.getInstance(project);
    if (manager != null) {
      final ToolWindow window = manager.getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
      if (window != null) {
        window.show(null);
        final ContentManager cm = window.getContentManager();
        final Content content = cm.findContent(SvnBundle.message("dialog.show.svn.map.title"));
        if (content != null) {
          cm.setSelectedContent(content, true);
        }
      }
    }
  }
}
