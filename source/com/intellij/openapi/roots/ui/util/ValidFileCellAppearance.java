package com.intellij.openapi.roots.ui.util;

import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.io.File;

public class ValidFileCellAppearance extends BaseTextCommentCellAppearance {
  private final VirtualFile myFile;

  public ValidFileCellAppearance(VirtualFile file) {
    myFile = file;
  }

  protected Icon getIcon() {
    return FileTypeManagerImpl.getInstance().getFileTypeByFile(myFile).getIcon();
  }

  protected String getSecondaryText() {
    return getSubname(true);
  }

  protected String getPrimaryText() {
    return getSubname(false);
  }

  private String getSubname(boolean headOrTail) {
    String presentableUrl = myFile.getPresentableUrl();
    int separatorIndex = getSplitUrlIndex(presentableUrl);
    if (headOrTail)
      return separatorIndex >= 0 ? presentableUrl.substring(0, separatorIndex) : "";
    else
      return presentableUrl.substring(separatorIndex + 1);
  }

  protected int getSplitUrlIndex(String presentableUrl) {
    return presentableUrl.lastIndexOf(File.separatorChar);
  }

  public VirtualFile getFile() {
    return myFile;
  }
}
