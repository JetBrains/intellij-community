package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * @author max
 */
public class ChangesBrowserNode extends DefaultMutableTreeNode {
  private Project myProject;
  private int count = -1;
  private int myDirectoryCount = -1;

  public ChangesBrowserNode(final Project project, Object userObject) {
    super(userObject);
    myProject = project;
    if ((userObject instanceof Change && !ChangesUtil.getFilePath((Change) userObject).isDirectory()) ||
        (userObject instanceof VirtualFile && !((VirtualFile) userObject).isDirectory()) ||
        userObject instanceof FilePath && !((FilePath)userObject).isDirectory()) {
      count = 1;
    }
  }

  public void insert(MutableTreeNode newChild, int childIndex) {
    super.insert(newChild, childIndex);
    count = -1;
    myDirectoryCount = -1;
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

  public int getDirectoryCount() {
    if (myDirectoryCount == -1) {
      if (userObject instanceof Change && ChangesUtil.getFilePath((Change) userObject).isDirectory()) {
        myDirectoryCount = 1;
      }
      else if (userObject instanceof FilePath && ((FilePath) userObject).isDirectory() && isLeaf()) {
        myDirectoryCount = 1;
      }
      else if (userObject instanceof VirtualFile && ((VirtualFile) userObject).isDirectory() &&
               FileStatusManager.getInstance(myProject).getStatus((VirtualFile) userObject) != FileStatus.NOT_CHANGED) {
        myDirectoryCount = 1;
      }
      else {
        myDirectoryCount = 0;
      }

      final Enumeration nodes = children();
      while (nodes.hasMoreElements()) {
        ChangesBrowserNode child = (ChangesBrowserNode)nodes.nextElement();
        myDirectoryCount += child.getDirectoryCount();
      }
    }
    return myDirectoryCount;
  }

  public List<Change> getAllChangesUnder() {
    List<Change> changes = new ArrayList<Change>();
    final Enumeration enumeration = breadthFirstEnumeration();
    while (enumeration.hasMoreElements()) {
      ChangesBrowserNode child = (ChangesBrowserNode)enumeration.nextElement();
      final Object value = child.getUserObject();
      if (value instanceof Change) {
        changes.add((Change)value);
      }
    }
    return changes;
  }

  public List<VirtualFile> getAllFilesUnder() {
    List<VirtualFile> files = new ArrayList<VirtualFile>();
    final Enumeration enumeration = breadthFirstEnumeration();
    while (enumeration.hasMoreElements()) {
      ChangesBrowserNode child = (ChangesBrowserNode)enumeration.nextElement();
      final Object value = child.getUserObject();
      if (value instanceof VirtualFile) {
        final VirtualFile file = (VirtualFile)value;
        if (file.isValid()) {
          files.add(file);
        }
      }
    }

    return files;
  }

  public List<FilePath> getAllFilePathsUnder() {
    List<FilePath> files = new ArrayList<FilePath>();
    final Enumeration enumeration = breadthFirstEnumeration();
    while (enumeration.hasMoreElements()) {
      ChangesBrowserNode child = (ChangesBrowserNode)enumeration.nextElement();
      final Object value = child.getUserObject();
      if (child.isLeaf() && value instanceof FilePath) {
        final FilePath file = (FilePath)value;
        files.add(file);
      }
    }

    return files;
  }
}
