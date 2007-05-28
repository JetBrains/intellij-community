package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public interface IComponentStore {
  void initComponent(Object component);
  void load() throws IOException;


  List<VirtualFile> getAllStorageFiles(final boolean includingSubStructures);

  class SaveCancelledException extends IOException {

    public SaveCancelledException() {
    }

    public SaveCancelledException(final String s) {
      super(s);
    }
  }

  SaveSession startSave() throws IOException;

  interface SaveSession {
    Collection<String> getUsedMacros() throws StateStorage.StateStorageException;
    List<VirtualFile> getAllStorageFilesToSave(final boolean includingSubStructures) throws IOException;
    SaveSession save() throws IOException;
    void finishSave();
  }

}
