// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.vcs.changes.committed.ChangeListFilteringStrategy;
import com.intellij.openapi.vcs.changes.committed.DecoratorManager;
import icons.SvnIcons;
import org.jetbrains.idea.svn.SvnBundle;

/**
* @author Konstantin Kolosovsky.
*/
public class ShowHideMergePanelAction extends DumbAwareToggleAction {

  private final DecoratorManager myManager;
  private final ChangeListFilteringStrategy myStrategy;
  private boolean myIsSelected;

  public ShowHideMergePanelAction(final DecoratorManager manager, final ChangeListFilteringStrategy strategy) {
    myManager = manager;
    myStrategy = strategy;
  }

  @Override
  public void update(final AnActionEvent e) {
    super.update(e);
    final Presentation presentation = e.getPresentation();
    presentation.setIcon(SvnIcons.ShowIntegratedFrom);
    presentation.setText(SvnBundle.message("committed.changes.action.enable.merge.highlighting"));
    presentation.setDescription(SvnBundle.message("committed.changes.action.enable.merge.highlighting.description.text"));
  }

  public boolean isSelected(final AnActionEvent e) {
    return myIsSelected;
  }

  public void setSelected(final AnActionEvent e, final boolean state) {
    myIsSelected = state;
    if (state) {
      myManager.setFilteringStrategy(myStrategy);
    } else {
      myManager.removeFilteringStrategy(myStrategy.getKey());
    }
  }
}
