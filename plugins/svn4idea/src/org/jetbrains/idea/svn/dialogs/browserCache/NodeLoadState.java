package org.jetbrains.idea.svn.dialogs.browserCache;

public enum NodeLoadState {
  EMPTY,  
  LOADING,  // for children
  ERROR,    // for children
  CACHED,
  REFRESHED
}
