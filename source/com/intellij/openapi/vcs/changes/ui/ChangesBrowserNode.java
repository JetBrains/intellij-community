package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.awt.*;

/**
 * @author max
 */
public class ChangesBrowserNode extends DefaultMutableTreeNode {
  protected int myCount = -1;
  private int myDirectoryCount = -1;

  protected ChangesBrowserNode(Object userObject) {
    super(userObject);
  }

  public static ChangesBrowserNode create(final Project project, Object userObject) {
    if (userObject instanceof Change) {
      return new ChangesBrowserChangeNode((Change) userObject);
    }
    if (userObject instanceof VirtualFile) {
      return new ChangesBrowserFileNode(project, (VirtualFile) userObject);
    }
    if (userObject instanceof FilePath) {
      return new ChangesBrowserFilePathNode((FilePath) userObject);
    }
    if (userObject instanceof ChangeList) {
      return new ChangesBrowserChangeListNode(project, (ChangeList) userObject);
    }
    if (userObject instanceof Module) {
      return new ChangesBrowserModuleNode((Module) userObject);
    }
    return new ChangesBrowserNode(userObject);
  }

  public void insert(MutableTreeNode newChild, int childIndex) {
    super.insert(newChild, childIndex);
    myCount = -1;
    myDirectoryCount = -1;
  }

  public int getCount() {
    if (myCount == -1) {
      myCount = 0;
      final Enumeration nodes = children();
      while (nodes.hasMoreElements()) {
        ChangesBrowserNode child = (ChangesBrowserNode)nodes.nextElement();
        myCount += child.getCount();
      }
    }
    return myCount;
  }

  public int getDirectoryCount() {
    if (myDirectoryCount == -1) {
      myDirectoryCount = isDirectory() ? 1 : 0;

      final Enumeration nodes = children();
      while (nodes.hasMoreElements()) {
        ChangesBrowserNode child = (ChangesBrowserNode)nodes.nextElement();
        myDirectoryCount += child.getDirectoryCount();
      }
    }
    return myDirectoryCount;
  }

  protected boolean isDirectory() {
    return false;
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

  public void render(final ChangesBrowserNodeRenderer renderer, final boolean selected, final boolean expanded, final boolean hasFocus) {
    renderer.append(userObject.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    appendCount(renderer);
  }

  protected void appendCount(final ColoredTreeCellRenderer renderer) {
    int count = getCount();
    int dirCount = getDirectoryCount();
    if (dirCount == 0) {
      renderer.append(" " + VcsBundle.message("changes.nodetitle.changecount", count), SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
    }
    else if (count == 0 && dirCount > 0) {
      renderer.append(" " + VcsBundle.message("changes.nodetitle.directory.changecount", dirCount), SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
    }
    else {
      renderer.append(" " + VcsBundle.message("changes.nodetitle.directory.file.changecount", dirCount, count), SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
    }
  }
}
