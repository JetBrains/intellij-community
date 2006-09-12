package com.intellij.openapi.vcs.update;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;

import javax.swing.*;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;

/**
 * author: lesya
 */
public class FileTreeNode extends FileOrDirectoryTreeNode {
  private static final Collection<VirtualFile> EMPTY_VIRTUAL_FILE_ARRAY = new ArrayList<VirtualFile>();


  public FileTreeNode(String path, SimpleTextAttributes invalidAttributes,
                      Project project, String parentPath) {
    super(path, invalidAttributes, project, parentPath);
  }

  public Icon getIcon(boolean expanded) {
    if (myFile.isDirectory()) {
      return Icons.DIRECTORY_CLOSED_ICON;
    }
    return FileTypeManager.getInstance().getFileTypeByFileName(myFile.getName()).getIcon();
  }

  public Collection<VirtualFile> getVirtualFiles() {
    VirtualFile virtualFile = getFilePointer().getFile();
    if (virtualFile == null) return EMPTY_VIRTUAL_FILE_ARRAY;
    return Collections.singleton(virtualFile);
  }

  public Collection<File> getFiles() {
    if (getFilePointer().getFile() == null) {
      return Collections.singleton(myFile);
    }
    else {
      return EMPTY_FILE_ARRAY;
    }
  }

  protected int getItemsCount() {
    return 1;
  }

  protected boolean showStatistics() {
    return false;
  }

}
