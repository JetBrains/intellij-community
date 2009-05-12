package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.ChangeListOwner;
import com.intellij.openapi.vcs.changes.LocallyDeletedChange;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class ChangesBrowserLocallyDeletedNode extends ChangesBrowserNode implements TreeLinkMouseListener.HaveTooltip {
  private final Project myProject;

  public ChangesBrowserLocallyDeletedNode(LocallyDeletedChange userObject, Project project) {
    super(userObject);
    myProject = project;
    myCount = 1;
  }

  public boolean canAcceptDrop(final ChangeListDragBean dragBean) {
    return false;
  }

  public void acceptDrop(final ChangeListOwner dragOwner, final ChangeListDragBean dragBean) {
  }

  @Override
  protected FilePath getMyPath() {
    final LocallyDeletedChange change = (LocallyDeletedChange) getUserObject();
    if (change != null) {
      return change.getPath();
    }
    return null;
  }

  @Override
  public void render(ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    // todo would be good to have render code in one place
    final LocallyDeletedChange change = (LocallyDeletedChange)getUserObject();
    final FilePath filePath = change.getPath();

    final String fileName = filePath.getName();
    VirtualFile vFile = filePath.getVirtualFile();
    final Color changeColor = FileStatus.NOT_CHANGED.getColor();
    renderer.appendFileName(vFile, fileName, changeColor);

    if (renderer.isShowFlatten()) {
      final File parentFile = filePath.getIOFile().getParentFile();
      if (parentFile != null) {
        renderer.append(" (" + parentFile.getPath() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    }
    else if (getCount() != 1 || getDirectoryCount() != 0) {
      appendCount(renderer);
    }

    final Icon addIcon = change.getAddIcon();
    if (addIcon != null) {
      renderer.setIcon(addIcon);
    } else {
      if (filePath.isDirectory() || !isLeaf()) {
        renderer.setIcon(expanded ? Icons.DIRECTORY_OPEN_ICON : Icons.DIRECTORY_CLOSED_ICON);
      }
      else {
        renderer.setIcon(filePath.getFileType().getIcon());
      }
    }
  }

  public String getTooltip() {
    final LocallyDeletedChange change = (LocallyDeletedChange)getUserObject();
    return change.getDescription();
  }
}
