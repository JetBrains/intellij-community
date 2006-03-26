package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Enumeration;

/**
 * @author max
 */
public class ChangesBrowserNode extends DefaultMutableTreeNode {
  private int count = -1;

  public ChangesBrowserNode(Object userObject) {
    super(userObject);
    if (userObject instanceof Change || userObject instanceof VirtualFile ||
        userObject instanceof FilePath && !((FilePath)userObject).isDirectory()) {
      count = 1;
    }
  }

  public int getCount() {
    if (count == -1) {
      count = 0;
      final Enumeration nodes = children();
      while (nodes.hasMoreElements()) {
        ChangesBrowserNode child = (ChangesBrowserNode)nodes.nextElement();
        count += child.getCount();
      }
    }
    return count;
  }
}
