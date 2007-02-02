/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

/**
 * @author yole
*/
class ChangeListDragBean {
  private ChangesListView myView;
  private Change[] myChanges;
  private List<VirtualFile> myUnversionedFiles;
  private ChangesBrowserNode myTargetNode;

  public ChangeListDragBean(final ChangesListView view, final Change[] changes, final List<VirtualFile> unversionedFiles) {
    myView = view;
    myChanges = changes;
    myUnversionedFiles = unversionedFiles;
  }

  public ChangesListView getView() {
    return myView;
  }

  public Change[] getChanges() {
    return myChanges;
  }

  public List<VirtualFile> getUnversionedFiles() {
    return myUnversionedFiles;
  }

  public ChangesBrowserNode getTargetNode() {
    return myTargetNode;
  }

  public void setTargetNode(final ChangesBrowserNode targetNode) {
    myTargetNode = targetNode;
  }
}