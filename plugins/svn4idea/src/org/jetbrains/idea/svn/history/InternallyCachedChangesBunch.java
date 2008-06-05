package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.changes.committed.ChangesBunch;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;

import java.util.List;

public class InternallyCachedChangesBunch extends ChangesBunch {
  private int mySelfAddress;
  private int myPreviousAddress;

  public InternallyCachedChangesBunch(final List<CommittedChangeList> list, final boolean consistentWithPrevious,
                                      final int selfAddress,
                                      final int previousAddress) {
    super(list, consistentWithPrevious);
    mySelfAddress = selfAddress;
    myPreviousAddress = previousAddress;
  }

  public int getSelfAddress() {
    return mySelfAddress;
  }

  public int getPreviousAddress() {
    return myPreviousAddress;
  }
}
