package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.application.ApplicationManager;

import java.util.HashSet;
import java.util.Set;

public class BackgroundableActionEnabledHandler {
  private final Set<Object> myInProgress;

  public BackgroundableActionEnabledHandler() {
    myInProgress = new HashSet<Object>();
  }

  public void register(final Object path) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myInProgress.add(path);
  }

  public boolean isInProgress(final Object path) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myInProgress.contains(path);
  }

  public void completed(final Object path) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myInProgress.remove(path);
  }
}
