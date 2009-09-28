package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;

import javax.swing.*;

public class ConflictedSvnChange extends Change {
  private final ConflictState myConflictState;
  // +-
  private final FilePath myTreeConflictMarkHolder;

  public ConflictedSvnChange(ContentRevision beforeRevision, ContentRevision afterRevision, final ConflictState conflictState,
                             final FilePath treeConflictMarkHolder) {
    super(beforeRevision, afterRevision);
    myConflictState = conflictState;
    myTreeConflictMarkHolder = treeConflictMarkHolder;
  }

  public ConflictedSvnChange(ContentRevision beforeRevision, ContentRevision afterRevision, FileStatus fileStatus,
                             final ConflictState conflictState, final FilePath treeConflictMarkHolder) {
    super(beforeRevision, afterRevision, fileStatus);
    myConflictState = conflictState;
    myTreeConflictMarkHolder = treeConflictMarkHolder;
  }

  public ConflictState getConflictState() {
    return myConflictState;
  }

  @Override
  public Icon getAdditionalIcon() {
    return myConflictState.getIcon();
  }

  @Override
  public String getDescription() {
    final String description = myConflictState.getDescription();
    if (description != null) {
      return SvnBundle.message("svn.changeview.item.in.conflict.text", description);
    }
    return description;
  }

  public FilePath getTreeConflictMarkHolder() {
    return myTreeConflictMarkHolder;
  }
}
