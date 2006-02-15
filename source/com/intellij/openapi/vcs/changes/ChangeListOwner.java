package com.intellij.openapi.vcs.changes;

/**
 * @author max
 */
public interface ChangeListOwner {
  void moveChangesTo(ChangeList list, Change[] changes);
}
