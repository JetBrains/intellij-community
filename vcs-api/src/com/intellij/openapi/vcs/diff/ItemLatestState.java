package com.intellij.openapi.vcs.diff;

import com.intellij.openapi.vcs.history.VcsRevisionNumber;

/**
 * The latest state of the versioned item
 */
public class ItemLatestState {
  /** version number */
  private final VcsRevisionNumber myNumber;
  /** true if the item still exists in the remote reposistory */
  private final boolean myItemExists;

  /**
   * A constructor
   *
   * @param number version number
   * @param itemExists true if the item still exists in the remote reposistory
   */

  public ItemLatestState(final VcsRevisionNumber number, final boolean itemExists) {
    myNumber = number;
    myItemExists = itemExists;
  }

  /**
   * @return the latest version of the item
   */
  public VcsRevisionNumber getNumber() {
    return myNumber;
  }

  /**
   * @return true if the item exists in the remote reposistory
   */
  public boolean isItemExists() {
    return myItemExists;
  }
}
