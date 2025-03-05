// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.vcs.changes.committed.ChangeListFilteringStrategy;
import com.intellij.openapi.vcs.changes.committed.DecoratorManager;
import icons.SvnIcons;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.idea.svn.SvnBundle.messagePointer;

public class ShowHideMergePanelAction extends DumbAwareToggleAction {

  private final @NotNull DecoratorManager myManager;
  private final @NotNull ChangeListFilteringStrategy myStrategy;
  private boolean myIsSelected;

  public ShowHideMergePanelAction(@NotNull DecoratorManager manager, @NotNull ChangeListFilteringStrategy strategy) {
    super(
      messagePointer("action.Subversion.ShowIntegratePanel.text"),
      messagePointer("committed.changes.action.enable.merge.highlighting.description.text"),
      SvnIcons.PreviewDetailsLeft
    );
    myManager = manager;
    myStrategy = strategy;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public boolean isSelected(final @NotNull AnActionEvent e) {
    return myIsSelected;
  }

  @Override
  public void setSelected(final @NotNull AnActionEvent e, final boolean state) {
    myIsSelected = state;
    if (state) {
      myManager.setFilteringStrategy(myStrategy);
    } else {
      myManager.removeFilteringStrategy(myStrategy.getKey());
    }
  }
}
