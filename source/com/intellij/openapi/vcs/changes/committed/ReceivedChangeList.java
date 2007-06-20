package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * @author yole
 */
public class ReceivedChangeList extends CommittedChangeListImpl {
  @NotNull private CommittedChangeList myBaseList;
  private int myBaseCount;

  public ReceivedChangeList(@NotNull CommittedChangeList baseList) {
    super(baseList.getName(), baseList.getComment(), baseList.getCommitterName(),
          baseList.getNumber(), baseList.getCommitDate(), Collections.<Change>emptyList());
    myBaseList = baseList;
    myBaseCount = baseList.getChanges().size();
  }

  public void addChange(Change change) {
    myChanges.add(change);
  }

  public boolean isPartial() {
    return myChanges.size() < myBaseCount;
  }

  @Override
  public AbstractVcs getVcs() {
    return myBaseList.getVcs();
  }

  @NotNull
  public CommittedChangeList getBaseList() {
    return myBaseList;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ReceivedChangeList that = (ReceivedChangeList)o;

    if (!myBaseList.equals(that.myBaseList)) return false;

    return true;
  }

  public int hashCode() {
    return myBaseList.hashCode();
  }
}
