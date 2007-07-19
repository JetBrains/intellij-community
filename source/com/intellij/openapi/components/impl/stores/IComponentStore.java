package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.fs.IFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface IComponentStore {
  void initComponent(Object component);
  void load() throws IOException, StateStorage.StateStorageException;


  class SaveCancelledException extends IOException {

    public SaveCancelledException() {
    }

    public SaveCancelledException(final String s) {
      super(s);
    }
  }

  //todo:remove throws
  @NotNull
  SaveSession startSave() throws IOException;

  interface SaveSession {
    Collection<String> getUsedMacros() throws StateStorage.StateStorageException;
    List<IFile> getAllStorageFilesToSave(final boolean includingSubStructures) throws IOException;
    SaveSession save() throws IOException;
    void finishSave();

    Set<String> analyzeExternalChanges(Set<Pair<VirtualFile,StateStorage>> changedFiles);
    List<IFile> getAllStorageFiles(final boolean includingSubStructures);
  }

}
