package org.jetbrains.idea.svn.history;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ChangeListColumn;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesNavigation;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTableModel;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;

public class SvnCommittedChangesTableModel extends CommittedChangesTableModel implements CommittedChangesNavigation {
  private final SvnRevisionsNavigationMediator myMediator;

  public SvnCommittedChangesTableModel(final SvnRepositoryLocation location, final Project project, final VirtualFile vcsRoot,
                                       final ChangeListColumn[] columns) throws VcsException {
    super(new ArrayList<CommittedChangeList>(), columns);
    myMediator = new SvnRevisionsNavigationMediator(location, project, vcsRoot);
    setItems(myMediator.getCurrent());
  }

  public boolean canGoBack() {
    return myMediator.canGoBack();
  }

  public boolean canGoForward() {
    return myMediator.canGoForward();
  }

  public void goBack() throws VcsException {
    myMediator.goBack();
    setItems(myMediator.getCurrent());
  }

  public void goForward() {
    myMediator.goForward();
    setItems(myMediator.getCurrent());
  }

  public void onBeforeClose() {
    myMediator.onBeforeClose();
  }

}
