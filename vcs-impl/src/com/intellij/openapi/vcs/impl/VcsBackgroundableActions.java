package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;

public enum VcsBackgroundableActions {
  ANNOTATE,
  COMPARE_WITH, // common for compare with (selected/latest/same) revision
  CREATE_HISTORY_SESSION,
  HISTORY_FOR_SELECTION;

  public static Object keyFrom(final FilePath filePath) {
    return filePath.getPath();
  }

  public static String keyFrom(final VirtualFile vf) {
    return vf.getPath();
  }
}
