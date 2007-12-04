/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.util.List;

/**
 * @author yole
*/
class ChangeListDragBean {
  private JComponent mySourceComponent;
  private Change[] myChanges;
  private List<VirtualFile> myUnversionedFiles;
  private List<VirtualFile> myIgnoredFiles;
  private ChangesBrowserNode myTargetNode;

  public ChangeListDragBean(final JComponent sourceComponent, final Change[] changes, final List<VirtualFile> unversionedFiles,
                            final List<VirtualFile> ignoredFiles) {
    mySourceComponent = sourceComponent;
    myChanges = changes;
    myUnversionedFiles = unversionedFiles;
    myIgnoredFiles = ignoredFiles;
  }

  public JComponent getSourceComponent() {
    return mySourceComponent;
  }

  public Change[] getChanges() {
    return myChanges;
  }

  public List<VirtualFile> getUnversionedFiles() {
    return myUnversionedFiles;
  }

  public List<VirtualFile> getIgnoredFiles() {
    return myIgnoredFiles;
  }

  public ChangesBrowserNode getTargetNode() {
    return myTargetNode;
  }

  public void setTargetNode(final ChangesBrowserNode targetNode) {
    myTargetNode = targetNode;
  }
}