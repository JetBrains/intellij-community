package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.VcsException;

public interface CommittedChangesNavigation {
  boolean canGoBack();
  boolean canGoForward();
  void goBack() throws VcsException;
  void goForward();
  void onBeforeClose();
}
