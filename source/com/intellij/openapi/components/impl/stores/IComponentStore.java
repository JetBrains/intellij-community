package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface IComponentStore {
  void initComponent(Object component);
  void load() throws IOException, StateStorage.StateStorageException;


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

    Set<String> analyzeExternalChanges(Set<VirtualFile> changedFiles);
  }

}
