package com.intellij.ide.startup;

import com.intellij.openapi.vfs.VirtualFile;

public interface CacheUpdater {
  VirtualFile[] queryNeededFiles();
  void processFile(FileContent fileContent);
  void updatingDone();
  void canceled();
}
