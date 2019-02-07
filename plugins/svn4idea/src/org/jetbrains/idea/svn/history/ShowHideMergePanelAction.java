// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.vcs.changes.committed.ChangeListFilteringStrategy;
import com.intellij.openapi.vcs.changes.committed.DecoratorManager;
import icons.SvnIcons;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.idea.svn.SvnBundle.message;

public class ShowHideMergePanelAction extends DumbAwareToggleAction {

  private final DecoratorManager myManager;
  private final ChangeListFilteringStrategy myStrategy;
  private boolean myIsSelected;

  public ShowHideMergePanelAction(final DecoratorManager manager, final ChangeListFilteringStrategy strategy) {
    super("Show Integrate Panel", message("committed.changes.action.enable.merge.highlighting.description.text"),
          SvnIcons.PreviewDetailsLeft);
    myManager = manager;
    myStrategy = strategy;
  }

  @Override
  public boolean isSelected(@NotNull final AnActionEvent e) {
    return myIsSelected;
  }

  @Override
  public void setSelected(@NotNull final AnActionEvent e, final boolean state) {
    myIsSelected = state;
    if (state) {
      myManager.setFilteringStrategy(myStrategy);
    } else {
      myManager.removeFilteringStrategy(myStrategy.getKey());
    }
  }
}
