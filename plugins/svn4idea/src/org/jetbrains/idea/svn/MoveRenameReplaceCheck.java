package org.jetbrains.idea.svn;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.changes.Change;

public class MoveRenameReplaceCheck {
  public static boolean check(final Change c) {
    if (c.getAfterRevision() == null || c.getBeforeRevision() == null) return false;
    return c.isIsReplaced() || c.isMoved() || c.isRenamed() || (! Comparing.equal(c.getBeforeRevision().getFile(), c.getAfterRevision().getFile()));
  }
}
