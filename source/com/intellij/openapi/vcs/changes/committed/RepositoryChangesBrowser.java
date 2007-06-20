package com.intellij.openapi.vcs.changes.committed;

import com.intellij.ide.actions.EditSourceAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.actions.OpenRepositoryVersionAction;
import com.intellij.openapi.vcs.changes.actions.ShowDiffWithLocalAction;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class RepositoryChangesBrowser extends ChangesBrowser {
  public RepositoryChangesBrowser(final Project project, final List<CommittedChangeList> changeLists) {
    super(project, changeLists, Collections.<Change>emptyList(), null, false, false);
  }

  public RepositoryChangesBrowser(final Project project, final List<? extends ChangeList> changeLists, final List<Change> changes,
                                  final ChangeList initialListSelection) {
    super(project, changeLists, changes, initialListSelection, false, false);
  }

  protected void buildToolBar(final DefaultActionGroup toolBarGroup) {
    super.buildToolBar(toolBarGroup);
    toolBarGroup.add(new ShowDiffWithLocalAction());
    final Icon editSourceIcon = IconLoader.getIcon("/actions/editSource.png");
    toolBarGroup.add(new EditSourceAction() {
      public void update(final AnActionEvent event) {
        super.update(event);
        event.getPresentation().setIcon(editSourceIcon);
        event.getPresentation().setText("Edit Source");
      }
    });
    OpenRepositoryVersionAction action = new OpenRepositoryVersionAction();
    toolBarGroup.add(action);
  }
}