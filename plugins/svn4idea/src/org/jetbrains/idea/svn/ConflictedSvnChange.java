// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import org.jetbrains.idea.svn.conflict.TreeConflictDescription;

import javax.swing.*;

import static org.jetbrains.idea.svn.SvnBundle.message;

public class ConflictedSvnChange extends Change {
  private final ConflictState myConflictState;
  // also used if not move/rename
  private TreeConflictDescription myBeforeDescription;
  private TreeConflictDescription myAfterDescription;
  // +-
  private final FilePath myTreeConflictMarkHolder;
  private boolean myIsPhantom;

  public ConflictedSvnChange(ContentRevision beforeRevision, ContentRevision afterRevision, final ConflictState conflictState,
                             final FilePath treeConflictMarkHolder) {
    super(beforeRevision, afterRevision);
    myConflictState = conflictState;
    myTreeConflictMarkHolder = treeConflictMarkHolder;
  }

  public ConflictedSvnChange(ContentRevision beforeRevision, ContentRevision afterRevision, FileStatus fileStatus,
                             final ConflictState conflictState,
                             final FilePath treeConflictMarkHolder) {
    super(beforeRevision, afterRevision, fileStatus);
    myConflictState = conflictState;
    myTreeConflictMarkHolder = treeConflictMarkHolder;
  }

  public ConflictState getConflictState() {
    return myConflictState;
  }

  public void setIsPhantom(boolean isPhantom) {
    myIsPhantom = isPhantom;
  }

  public boolean isTreeConflict() {
    return myConflictState.isTree();
  }

  public boolean isPhantom() {
    return myIsPhantom;
  }

  public TreeConflictDescription getBeforeDescription() {
    return myBeforeDescription;
  }

  public void setBeforeDescription(TreeConflictDescription beforeDescription) {
    myBeforeDescription = beforeDescription;
  }

  public TreeConflictDescription getAfterDescription() {
    return myAfterDescription;
  }

  public void setAfterDescription(TreeConflictDescription afterDescription) {
    myAfterDescription = afterDescription;
  }

  @Override
  public Icon getAdditionalIcon() {
    return myConflictState.getIcon();
  }

  @Override
  public String getDescription() {
    String description = myConflictState.getDescription();
    if (description == null) return null;

    return myBeforeDescription != null
           ? myAfterDescription != null
             ? message("label.file.has.conflicts.before.and.after", description, myBeforeDescription.toPresentableString(),
                       myAfterDescription.toPresentableString())
             : message("label.file.has.conflicts.before.or.after", description, myBeforeDescription.toPresentableString())
           : myAfterDescription != null
             ? message("label.file.has.conflicts.before.or.after", description, myAfterDescription.toPresentableString())
             : message("label.file.has.conflicts", description);
  }

  public FilePath getTreeConflictMarkHolder() {
    return myTreeConflictMarkHolder;
  }
}
