package com.intellij.openapi.vcs.changes;

public enum InvokeAfterUpdateMode {
  SILENT(false, true, false),
  BACKGROUND_CANCELLABLE(true, false, false),
  BACKGROUND_NOT_CANCELLABLE(false, false, false),
  SYNCHRONOUS_CANCELLABLE(true, false, true),
  SYNCHRONOUS_NOT_CANCELLABLE(false, false, true);

  private final boolean myCancellable;
  private final boolean mySilently;
  private final boolean mySynchronous;

  InvokeAfterUpdateMode(final boolean cancellable, final boolean silently, final boolean synchronous) {
    myCancellable = cancellable;
    mySilently = silently;
    mySynchronous = synchronous;
  }

  public boolean isCancellable() {
    return myCancellable;
  }

  public boolean isSilently() {
    return mySilently;
  }

  public boolean isSynchronous() {
    return mySynchronous;
  }
}
