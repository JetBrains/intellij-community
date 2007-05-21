package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl;

import java.util.Collections;

/**
 * @author yole
 */
public class ReceivedChangeList extends CommittedChangeListImpl {
  private int myBaseCount;

  public ReceivedChangeList(CommittedChangeList baseList) {
    super(baseList.getName(), baseList.getComment(), baseList.getCommitterName(),
          baseList.getNumber(), baseList.getCommitDate(), Collections.<Change>emptyList());
    myBaseCount = baseList.getChanges().size();
  }

  public void addChange(Change change) {
    myChanges.add(change);
  }

  public boolean isPartial() {
    return myChanges.size() < myBaseCount;
  }
}