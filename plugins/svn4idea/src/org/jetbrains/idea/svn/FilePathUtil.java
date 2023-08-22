// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;

import java.io.File;
import java.util.Collection;

public final class FilePathUtil {
  private FilePathUtil() {
  }

  public static boolean isNested(final Collection<? extends FilePath> roots, final FilePath root) {
    return isNested(roots, root.getIOFile());
  }

  public static boolean isNested(final Collection<? extends FilePath> roots, final File root) {
    for (FilePath filePath : roots) {
      final File ioFile = filePath.getIOFile();

      if (FileUtil.isAncestor(ioFile, root, true)) {
        return true;
      }
    }
    return false;
  }
}
