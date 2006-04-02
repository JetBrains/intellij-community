package com.intellij.openapi.vcs.changes;

/**
 * @author max
 */
public interface ChangeListOwner {
  void moveChangesTo(LocalChangeList list, Change[] changes);
}
