package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class FoldersCutDownWorker {
  private final Set<String> myPaths;

  public FoldersCutDownWorker() {
    myPaths = new HashSet<String>();
  }

  public boolean addCurrent(final VirtualFile file) {
    final String currentPath = filePath(file.getPath());
    final boolean result = underCollectedRoots(currentPath);
    if (! result) {
      myPaths.add(currentPath);
    }
    return ! result;
  }

  private static String filePath(final String file) {
    return file.replace('\\', File.separatorChar).replace('/', File.separatorChar);
  }

  public boolean underCollectedRoots(final File file) {
    return underCollectedRoots(filePath(file.getAbsolutePath()));
  }

  private boolean underCollectedRoots(final String currentPath) {
    for (String path : myPaths) {
      if (currentPath.startsWith(path)) {
        return true;
      }
    }
    return false;
  }
}
