/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;

import java.awt.*;
import java.io.File;

/**
 * @author yole
 */
public class ChangesBrowserChangeNode extends ChangesBrowserNode<Change> {
  private Project myProject;

  protected ChangesBrowserChangeNode(final Project project, Change userObject) {
    super(userObject);
    myProject = project;
    if (!ChangesUtil.getFilePath(userObject).isDirectory()) {
      myCount = 1;
    }
  }

  @Override
  protected boolean isDirectory() {
    return ChangesUtil.getFilePath(getUserObject()).isDirectory();
  }

  @Override
  public void render(final ChangesBrowserNodeRenderer renderer, final boolean selected, final boolean expanded, final boolean hasFocus) {
    final Change change = getUserObject();
    final FilePath filePath = ChangesUtil.getFilePath(change);
    final String fileName = filePath.getName();
    VirtualFile vFile = filePath.getVirtualFile();
    final Color changeColor = change.getFileStatus().getColor();
    renderer.appendFileName(vFile, fileName, changeColor);

    final String originText = change.getOriginText(myProject);
    if (originText != null) {
      renderer.append(" " + originText, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }

    if (renderer.isShowFlatten()) {
      final File parentFile = filePath.getIOFile().getParentFile();
      if (parentFile != null) {
        renderer.append(" (" + parentFile.getPath() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      appendSwitched(renderer);
    }
    else if (getCount() != 1 || getDirectoryCount() != 0) {
      appendSwitched(renderer);
      appendCount(renderer);
    }
    else {
      appendSwitched(renderer);
    }

    if (filePath.isDirectory() || !isLeaf()) {
      renderer.setIcon(expanded ? Icons.DIRECTORY_OPEN_ICON : Icons.DIRECTORY_CLOSED_ICON);
    }
    else {
      renderer.setIcon(filePath.getFileType().getIcon());
    }
  }

  private void appendSwitched(final ChangesBrowserNodeRenderer renderer) {
    final VirtualFile virtualFile = ChangesUtil.getFilePath(getUserObject()).getVirtualFile();
    if (virtualFile != null) {
      String branch = ChangeListManager.getInstance(myProject).getSwitchedBranch(virtualFile);
      if (branch != null) {
        renderer.append(" [switched to " + branch + "]", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
  }

  @Override
  public String getTextPresentation() {
    return ChangesUtil.getFilePath(getUserObject()).getName();
  }

  @Override
  public String toString() {
    return FileUtil.toSystemDependentName(ChangesUtil.getFilePath(getUserObject()).getPath());
  }

  public int getSortWeight() {
    return 6;
  }

  public int compareUserObjects(final Object o2) {
    if (o2 instanceof Change) {
      return ChangesUtil.getFilePath(getUserObject()).getName().compareToIgnoreCase(ChangesUtil.getFilePath((Change)o2).getName());
    }
    return 0;
  }
}