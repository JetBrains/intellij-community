package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.ChangeListsMergerFactory;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.tmatesoft.svn.core.SVNURL;

import java.util.ArrayList;
import java.util.List;

public class SelectedChangeListsChecker implements SelectedCommittedStuffChecker {
  protected final List<CommittedChangeList> myChangeListsList;
  private boolean isValid;
  private SVNURL mySameBranch;
  private VirtualFile myVcsRoot;
  private final Consumer<List<CommittedChangeList>> myAfterProcessing;

  public SelectedChangeListsChecker(final Consumer<List<CommittedChangeList>> afterProcessing) {
    myAfterProcessing = afterProcessing;
    myChangeListsList = new ArrayList<CommittedChangeList>();
  }

  public void execute(final AnActionEvent event) {
    isValid = false;
    myChangeListsList.clear();

    getSelectedSvnCls(event);
    if (! myChangeListsList.isEmpty()) {
      checkSame();
    }
  }

  public boolean isValid() {
    return isValid;
  }

  public List<CommittedChangeList> getSelectedLists() {
    return myChangeListsList;
  }

  public MergerFactory createFactory() {
    return new ChangeListsMergerFactory(myChangeListsList, myAfterProcessing);
  }

  private void checkSame() {
    final CheckSamePattern<SVNURL> sameBranch = new CheckSamePattern<SVNURL>();
    final CheckSamePattern<VirtualFile> sameRoot = new CheckSamePattern<VirtualFile>();

    for (ChangeList changeList : myChangeListsList) {
      final SvnChangeList svnChangeList = (SvnChangeList) changeList;
      sameBranch.iterate(svnChangeList.getBranchUrl());
      sameRoot.iterate(svnChangeList.getRoot());

      if ((! sameBranch.isSame()) || (! sameRoot.isSame())) {
        break;
      }
    }
    isValid = sameBranch.isSame() && sameRoot.isSame();
    mySameBranch = sameBranch.getSameValue();
    myVcsRoot = sameRoot.getSameValue();
  }

  public SVNURL getSameBranch() {
    return mySameBranch;
  }

  public VirtualFile getRoot() {
    return myVcsRoot;
  }

  private void getSelectedSvnCls(final AnActionEvent event) {
    final ChangeList[] cls = event.getData(VcsDataKeys.CHANGE_LISTS);

    if (cls != null) {
      for (ChangeList cl : cls) {
        final CommittedChangeList committed = ((CommittedChangeList) cl);
        if ((committed != null) && (committed.getVcs() != null) && (SvnVcs.VCS_NAME.equals(committed.getVcs().getName()))) {
          myChangeListsList.add(committed);
        }
      }
    }
  }
}
