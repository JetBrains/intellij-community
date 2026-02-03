// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ChangeListColumn;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesNavigation;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTableModel;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class SvnCommittedChangesTableModel extends CommittedChangesTableModel implements CommittedChangesNavigation {
  private final SvnRevisionsNavigationMediator myMediator;

  public SvnCommittedChangesTableModel(final SvnRepositoryLocation location, final Project project, final VirtualFile vcsRoot,
                                       ChangeListColumn @NotNull [] columns) throws VcsException {
    super(new ArrayList<>(), columns, false);
    myMediator = new SvnRevisionsNavigationMediator(location, project, vcsRoot);
    setItems(new ArrayList<>(myMediator.getCurrent()));
  }

  @Override
  public boolean canGoBack() {
    return myMediator.canGoBack();
  }

  @Override
  public boolean canGoForward() {
    return myMediator.canGoForward();
  }

  @Override
  public void goBack() throws VcsException {
    myMediator.goBack();
    setItems(new ArrayList<>(myMediator.getCurrent()));
  }

  @Override
  public void goForward() {
    myMediator.goForward();
    setItems(new ArrayList<>(myMediator.getCurrent()));
  }

  @Override
  public void onBeforeClose() {
    myMediator.onBeforeClose();
  }

}
