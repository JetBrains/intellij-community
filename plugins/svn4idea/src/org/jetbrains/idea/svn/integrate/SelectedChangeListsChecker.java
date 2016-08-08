/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.CheckSamePattern;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.tmatesoft.svn.core.SVNURL;

import java.util.ArrayList;
import java.util.List;

public class SelectedChangeListsChecker implements SelectedCommittedStuffChecker {
  protected final List<CommittedChangeList> myChangeListsList;
  private boolean isValid;
  private SVNURL mySameBranch;
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
    final CheckSamePattern<SVNURL> sameBranch = new CheckSamePattern<>();
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
