/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.changes.ChangeListOwner;

/**
 * @author yole
 */
public class ChangesBrowserIgnoredFilesNode extends ChangesBrowserNode {
  protected ChangesBrowserIgnoredFilesNode(Object userObject) {
    super(userObject);
  }

  @Override
  public boolean canAcceptDrop(final ChangeListDragBean dragBean) {
    return dragBean.getUnversionedFiles().size() > 0;
  }

  @Override
  public void acceptDrop(final ChangeListOwner dragOwner, final ChangeListDragBean dragBean) {
    IgnoreUnversionedDialog.ignoreSelectedFiles(dragOwner.getProject(), dragBean.getUnversionedFiles());
  }
}