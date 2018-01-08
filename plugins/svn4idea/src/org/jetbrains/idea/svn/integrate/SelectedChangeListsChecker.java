// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.history.SvnChangeList;

import java.util.ArrayList;
import java.util.List;

public class SelectedChangeListsChecker implements SelectedCommittedStuffChecker {
  protected final List<CommittedChangeList> myChangeListsList;
  private boolean isValid;
  private Url mySameBranch;
  private VirtualFile myVcsRoot;

  public SelectedChangeListsChecker() {
    myChangeListsList = new ArrayList<>();
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

  private void checkSame() {
    final CheckSamePattern<Url> sameBranch = new CheckSamePattern<>();
    final CheckSamePattern<VirtualFile> sameRoot = new CheckSamePattern<>();

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

  public Url getSameBranch() {
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
