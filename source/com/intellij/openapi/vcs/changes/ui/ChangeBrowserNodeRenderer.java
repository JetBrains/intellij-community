package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;

import javax.swing.*;
import java.awt.*;

/**
 * @author max
 */
class ChangeBrowserNodeRenderer extends ColoredTreeCellRenderer {
  private final boolean myShowFlatten;
  private final Project myProject;
  private ChangeListDecorator[] myDecorators;

  public ChangeBrowserNodeRenderer(final Project project, final boolean showFlatten) {
    myShowFlatten = showFlatten;
    myProject = project;
    myDecorators = myProject.getComponents(ChangeListDecorator.class);
  }

  public void customizeCellRenderer(JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    ChangesBrowserNode node = (ChangesBrowserNode)value;
    Object object = node.getUserObject();
    if (object instanceof ChangeList) {
      if (object instanceof LocalChangeList) {
        final LocalChangeList list = ((LocalChangeList)object);
        append(list.getName(),
               list.isDefault() ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
        appendCount(node);
        for(ChangeListDecorator decorator: myDecorators) {
          decorator.decorateChangeList(list, this, selected, expanded, hasFocus);
        }
        if (list.isInUpdate()) {
          append(" " + VcsBundle.message("changes.nodetitle.updating"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
      }
      else {
        final ChangeList list = ((ChangeList)object);
        append(list.getName(), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
        appendCount(node);
      }
    }
    else if (object instanceof Change) {
      final Change change = (Change)object;
      final FilePath filePath = ChangesUtil.getFilePath(change);
      append(filePath.getName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, getColor(change), null));
      if (myShowFlatten) {
        append(" (" + filePath.getIOFile().getParentFile().getPath() + ", " + getChangeStatus(change).getText() + ")",
               SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      else if (node.getCount() != 1 || node.getDirectoryCount() != 0) {
        appendCount(node);
      }

      if (filePath.isDirectory()) {
        setIcon(Icons.DIRECTORY_CLOSED_ICON);
      }
      else {
        setIcon(filePath.getFileType().getIcon());
      }
    }
    else if (object instanceof VirtualFile) {
      final VirtualFile file = (VirtualFile)object;
      append(file.getName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, FileStatus.COLOR_UNKNOWN));
      if (myShowFlatten && file.isValid()) {
        final VirtualFile parentFile = file.getParent();
        assert parentFile != null;
        append(" (" + parentFile.getPresentableUrl() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      setIcon(file.getFileType().getIcon());
    }
    else if (object instanceof FilePath) {
      final FilePath path = (FilePath)object;
      if (path.isDirectory() || !node.isLeaf()) {
        append(ChangesListView.getRelativePath(ChangesListView.safeCastToFilePath(((ChangesBrowserNode)node.getParent()).getUserObject()), path),
               SimpleTextAttributes.REGULAR_ATTRIBUTES);
        appendCount(node);
        setIcon(expanded ? Icons.DIRECTORY_OPEN_ICON : Icons.DIRECTORY_CLOSED_ICON);
      }
      else {
        if (myShowFlatten) {
          append(path.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          final FilePath parent = path.getParentPath();
          append(" (" + parent.getPresentableUrl() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        else {
          append(ChangesListView.getRelativePath(ChangesListView.safeCastToFilePath(((ChangesBrowserNode)node.getParent()).getUserObject()), path),
                 SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
        setIcon(path.getFileType().getIcon());
      }
    }
    else if (object instanceof Module) {
      final Module module = (Module)object;

      append(module.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      appendCount(node);
      setIcon(module.getModuleType().getNodeIcon(expanded));
    }
    else {
      append(object.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      appendCount(node);
    }
  }

  private void appendCount(final ChangesBrowserNode node) {
    int count = node.getCount();
    int dirCount = node.getDirectoryCount();
    if (dirCount == 0) {
      append(" " + VcsBundle.message("changes.nodetitle.changecount", count), SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
    }
    else if (count == 0 && dirCount > 1) {
      append(" " + VcsBundle.message("changes.nodetitle.directory.changecount", dirCount), SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
    }
    else {
      append(" " + VcsBundle.message("changes.nodetitle.directory.file.changecount", dirCount, count), SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
    }
  }

  private FileStatus getChangeStatus(Change change) {
    return change.getFileStatus();
  }

  private Color getColor(final Change change) {
    return getChangeStatus(change).getColor();
  }
}
