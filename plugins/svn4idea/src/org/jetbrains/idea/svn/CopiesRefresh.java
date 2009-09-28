package org.jetbrains.idea.svn;

public interface CopiesRefresh {
  void ensureInit();
  void asynchRequest();
  void synchRequest();
}
