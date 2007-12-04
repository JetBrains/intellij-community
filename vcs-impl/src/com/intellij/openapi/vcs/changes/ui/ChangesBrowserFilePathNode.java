/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;

import java.io.File;

/**
 * @author yole
 */
public class ChangesBrowserFilePathNode extends ChangesBrowserNode<FilePath> {
  public ChangesBrowserFilePathNode(FilePath userObject) {
    super(userObject);
    if (!userObject.isDirectory()) {
      myCount = 1;
    }
  }

  @Override
  protected boolean isDirectory() {
    return getUserObject().isDirectory() && isLeaf();
  }

  @Override
  public void render(final ChangesBrowserNodeRenderer renderer, final boolean selected, final boolean expanded, final boolean hasFocus) {
    final FilePath path = (FilePath)userObject;
    if (path.isDirectory() || !isLeaf()) {
      renderer.append(getRelativePath(safeCastToFilePath(((ChangesBrowserNode)getParent()).getUserObject()), path),
             SimpleTextAttributes.REGULAR_ATTRIBUTES);
      if (!isLeaf()) {
        appendCount(renderer);
      }
      renderer.setIcon(expanded ? Icons.DIRECTORY_OPEN_ICON : Icons.DIRECTORY_CLOSED_ICON);
    }
    else {
      if (renderer.isShowFlatten()) {
        renderer.append(path.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        final FilePath parentPath = path.getParentPath();
        renderer.append(" (" + parentPath.getPresentableUrl() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      else {
        renderer.append(getRelativePath(safeCastToFilePath(((ChangesBrowserNode)getParent()).getUserObject()), path),
                        SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      renderer.setIcon(path.getFileType().getIcon());
    }
  }

  @Override
  public String getTextPresentation() {
    return getUserObject().getName();
  }

  @Override
  public String toString() {
    return FileUtil.toSystemDependentName(getUserObject().getPath());
  }

  public static FilePath safeCastToFilePath(Object o) {
    if (o instanceof FilePath) return (FilePath)o;
    return null;
  }

  public static String getRelativePath(FilePath parent, FilePath child) {
    if (parent == null) return child.getPath().replace('/', File.separatorChar);
    return child.getPath().substring(parent.getPath().length() + 1).replace('/', File.separatorChar);
  }

  public int getSortWeight() {
    if (((FilePath)userObject).isDirectory()) return 4;
    return 5;
  }

  public int compareUserObjects(final Object o2) {
    if (o2 instanceof FilePath) {
      return getUserObject().getPath().compareToIgnoreCase(((FilePath)o2).getPath());
    }

    return 0;
  }
}