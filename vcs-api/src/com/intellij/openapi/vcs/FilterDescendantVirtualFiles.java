package com.intellij.openapi.vcs;

import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;

import java.util.Collections;
import java.util.List;

public class FilterDescendantVirtualFiles {
  private FilterDescendantVirtualFiles() {
  }

  public static void filter(final List<VirtualFile> in) {
    Collections.sort(in, FilePathComparator.getInstance());

    for (int i = 1; i < in.size(); i++) {
      final VirtualFile child = in.get(i);
      for (int j = i; j >= 0; --j) {
        final VirtualFile parent = in.get(j);
        if (VfsUtil.isAncestor(parent, child, true)) {
          in.remove(i);
          -- i;
          break;
        }
      }
    }
  }
}
