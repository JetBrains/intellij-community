package com.intellij.openapi.vcs.changes;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class ChangeListManagerEx extends ChangeListManager {
  @Nullable
  public abstract LocalChangeList getIdentityChangeList(Change change);
  public abstract boolean isInUpdate();
}