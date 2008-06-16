package com.intellij.openapi.vcs.diff;

import com.intellij.openapi.vcs.history.VcsRevisionNumber;

public class ItemLatestState {
  private final VcsRevisionNumber myNumber;
  private final boolean myItemExists;

  public ItemLatestState(final VcsRevisionNumber number, final boolean itemExists) {
    myNumber = number;
    myItemExists = itemExists;
  }

  public VcsRevisionNumber getNumber() {
    return myNumber;
  }

  public boolean isItemExists() {
    return myItemExists;
  }
}
