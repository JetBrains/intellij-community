package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.ChangeListsMergerFactory;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SelectedChangeListsChecker implements SelectedCommittedStuffChecker {
  protected final List<CommittedChangeList> myChangeListsList;
  private boolean isValid;

  public SelectedChangeListsChecker() {
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

  public List<CommittedChangeList> getChangeListsList() {
    return myChangeListsList;
  }

  public MergerFactory createFactory() {
    return new ChangeListsMergerFactory(myChangeListsList);
  }

  private void checkSame() {
    final CheckSamePattern<File> sameWcRoot = new CheckSamePattern<File>();
    final CheckSamePattern<SVNURL> sameBranch = new CheckSamePattern<SVNURL>();

    for (ChangeList changeList : myChangeListsList) {
      final SvnChangeList svnChangeList = (SvnChangeList) changeList;
      sameWcRoot.iterate(svnChangeList.getWCRootRoot());
      sameBranch.iterate(svnChangeList.getBranchUrl());

      if ((! sameBranch.isSame()) || (! sameWcRoot.isSame())) {
        break;
      }
    }
    isValid = sameBranch.isSame() && sameWcRoot.isSame();
  }

  public static class CheckSamePattern <T> {
    private boolean mySame;
    private T mySameValue;

    public CheckSamePattern() {
      mySameValue = null;
      mySame = true;
    }

    public void iterate(final T t) {
      if (t == null) {
        mySame = false;
        return;
      }
      if (mySameValue == null) {
        mySameValue = t;
        return;
      }
      mySame &= mySameValue.equals(t);
    }

    public boolean isSame() {
      return mySame;
    }

    public T getSameValue() {
      return mySameValue;
    }
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
