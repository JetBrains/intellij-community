// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.actions.VcsQuickListContentProvider;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roman.Chernyatchik
 */
public class SvnQuickListContentProvider implements VcsQuickListContentProvider {
  @Override
  public List<AnAction> getVcsActions(@Nullable Project project, @Nullable AbstractVcs activeVcs,
                                      @Nullable DataContext dataContext) {

    if (activeVcs == null || !SvnVcs.VCS_NAME.equals(activeVcs.getName())) {
      return null;
    }

    final ActionManager manager = ActionManager.getInstance();
    final List<AnAction> actions = new ArrayList<>();
    add("ChangesView.Move", manager, actions);
    add("Subversion.Copy", manager, actions);
    add("Subversion.Clenaup", manager, actions);
    return actions;
  }

  private void add(String actionName, ActionManager manager, List<AnAction> actions) {
    final AnAction action = manager.getAction(actionName);
    assert action != null;
    actions.add(action);
  }
}
