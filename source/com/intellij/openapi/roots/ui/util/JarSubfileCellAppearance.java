package com.intellij.openapi.roots.ui.util;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;

public class JarSubfileCellAppearance extends ValidFileCellAppearance {
  public JarSubfileCellAppearance(VirtualFile file) {
    super(file);
  }

  protected Icon getIcon() {
    return StdFileTypes.ARCHIVE.getIcon();
  }

  protected int getSplitUrlIndex(String url) {
    int jarNameEnd = url.lastIndexOf(JarFileSystem.JAR_SEPARATOR.charAt(0));
    String jarUrl = jarNameEnd >= 0 ? url.substring(0, jarNameEnd) : url;
    return super.getSplitUrlIndex(jarUrl);
  }
}
