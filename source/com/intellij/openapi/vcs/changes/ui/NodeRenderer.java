package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;

import javax.swing.*;
import java.awt.*;

/**
 * @author max
 */
class NodeRenderer extends ColoredTreeCellRenderer {
  private final boolean myShowFlatten;
  private final Project myProject;


  public NodeRenderer(final Project project, final boolean showFlatten) {
    myShowFlatten = showFlatten;
    myProject = project;
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
      final ChangeList list = ((ChangeList)object);
      append(list.getDescription(),
             list.isDefault() ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
      appendCount(node);
      if (list.isInUpdate()) {
        append(" " + VcsBundle.message("changes.nodetitle.updating"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
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
      append(ChangesListView.getRelativePath(ChangesListView.safeCastToFilePath(((ChangesBrowserNode)node.getParent()).getUserObject()), path),
             SimpleTextAttributes.REGULAR_ATTRIBUTES);
      if (path.isDirectory()) {
        appendCount(node);
        setIcon(expanded ? Icons.DIRECTORY_OPEN_ICON : Icons.DIRECTORY_CLOSED_ICON);
      }
      else {
        if (myShowFlatten) {
          final FilePath parent = TreeModelBuilder.getParentPath(path);
          append(" (" + parent.getPresentableUrl() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
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
    append(" " + VcsBundle.message("changes.nodetitle.changecount", node.getCount()), SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
  }

  private FileStatus getChangeStatus(Change change) {
    final VirtualFile vFile = ChangesUtil.getFilePath(change).getVirtualFile();
    if (vFile == null) return FileStatus.DELETED;
    return FileStatusManager.getInstance(myProject).getStatus(vFile);
  }

  private Color getColor(final Change change) {
    return getChangeStatus(change).getColor();
  }
}
