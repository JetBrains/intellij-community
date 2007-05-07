package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.actions.ShowDiffWithLocalAction;
import com.intellij.openapi.vcs.changes.actions.OpenRepositoryVersionAction;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.CommonShortcuts;

import java.util.List;
import java.util.Collections;

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
    OpenRepositoryVersionAction action = new OpenRepositoryVersionAction();
    action.registerCustomShortcutSet(CommonShortcuts.getEditSource(), this);
    toolBarGroup.add(action);
  }
}