package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.List;

public interface IComponentStore {
  void initComponent(Object component);

  void commit();
  boolean save() throws IOException;
  void load() throws IOException;

  List<VirtualFile> getAllStorageFiles(final boolean includingSubStructures);
  List<VirtualFile> getAllStorageFilesToSave(final boolean includingSubStructures);

  class SaveCancelledException extends IOException {

    public SaveCancelledException() {
    }

    public SaveCancelledException(final String s) {
      super(s);
    }
  }
}
