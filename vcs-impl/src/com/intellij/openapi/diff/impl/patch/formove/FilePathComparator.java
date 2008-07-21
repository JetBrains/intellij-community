package com.intellij.openapi.diff.impl.patch.formove;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.Comparator;

public final class FilePathComparator implements Comparator<VirtualFile> {
  private static final FilePathComparator ourInstance = new FilePathComparator();

  public static FilePathComparator getInstance() {
    return ourInstance;
  }

  public int compare(final VirtualFile o1, final VirtualFile o2) {
    return o1.getPath().compareTo(o2.getPath());
  }
}
