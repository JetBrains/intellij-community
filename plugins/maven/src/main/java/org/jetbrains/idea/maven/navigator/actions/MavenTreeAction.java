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
package org.jetbrains.idea.maven.navigator.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenDataKeys;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class MavenTreeAction extends AnAction implements DumbAware {
  private boolean updated = false;

  @Override
  public void update(AnActionEvent e) {
    if (!updated) { //don't do it in constructor, this is not gonna work
      final String name = getShortcutSetActionName();
      if (name != null) {
        final AnAction action = ActionManager.getInstance().getAction(name);
        if (action != null) {
          final String id = ActionManager.getInstance().getId(this);
          final Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
          for (Shortcut shortcut : action.getShortcutSet().getShortcuts()) {
            keymap.addShortcut(id, shortcut);
          }

        }
      }
      updated = true;
    }
    e.getPresentation().setEnabled(MavenActionUtil.hasProject(e.getDataContext())
                                   && MavenActionUtil.getProjectsManager(e.getDataContext()).isMavenizedProject());
  }

  @Nullable
  public String getShortcutSetActionName() {
    return null;
  }

  @Nullable
  protected static JTree getTree(AnActionEvent e) {
    return MavenDataKeys.MAVEN_PROJECTS_TREE.getData(e.getDataContext());
  }

  public static class CollapseAll extends MavenTreeAction {
    @Override
    public String getShortcutSetActionName() {
      return "CollapseAll";
    }

    public void actionPerformed(AnActionEvent e) {
      final JTree tree = getTree(e);

      if (tree == null) return;

      int row = tree.getRowCount() - 1;
      while (row >= 0) {
        tree.collapseRow(row);
        row--;
      }
    }
  }

  public static class ExpandAll extends MavenTreeAction {
    @Override
    public String getShortcutSetActionName() {
      return "ExpandAll";
    }

    public void actionPerformed(AnActionEvent e) {
      final JTree tree = getTree(e);

      if (tree == null) return;

      for (int i = 0; i < tree.getRowCount(); i++) {
        tree.expandRow(i);
      }
    }
  }
}
