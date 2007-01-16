/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;

/**
 * @author yole
 */
public class ChangesBrowserFilePathNode extends ChangesBrowserNode {
  public ChangesBrowserFilePathNode(FilePath userObject) {
    super(userObject);
    if (!userObject.isDirectory()) {
      myCount = 1;
    }
  }

  @Override
  protected boolean isDirectory() {
    return ((FilePath) userObject).isDirectory() && isLeaf();
  }


  @Override
  public void render(final ChangesBrowserNodeRenderer renderer, final boolean selected, final boolean expanded, final boolean hasFocus) {
    final FilePath path = (FilePath)userObject;
    if (path.isDirectory() || !isLeaf()) {
      renderer.append(ChangesListView.getRelativePath(ChangesListView.safeCastToFilePath(((ChangesBrowserNode)getParent()).getUserObject()), path),
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
        renderer.append(ChangesListView.getRelativePath(ChangesListView.safeCastToFilePath(((ChangesBrowserNode)getParent()).getUserObject()), path),
                        SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      renderer.setIcon(path.getFileType().getIcon());
    }
  }
}