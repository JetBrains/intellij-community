package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.StateStorageOperation;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * @author mike
 */
public interface StateStorageManager {
  void addMacro(String macro, String expansion);

  @Nullable
  StateStorage getStateStorage(@NotNull Storage storageSpec) throws StateStorage.StateStorageException;

  @Nullable
  StateStorage getFileStateStorage(String fileName);

  void clearStateStorage(@NotNull String file);

  List<VirtualFile> getAllStorageFiles();

  void reload(final Set<VirtualFile> changedFiles, final Set<String> changedComponents) throws StateStorage.StateStorageException;

  ExternalizationSession startExternalization();
  SaveSession startSave(ExternalizationSession externalizationSession) throws StateStorage.StateStorageException;
  void finishSave(SaveSession saveSession);

  @Nullable
  StateStorage getOldStorage(Object component, final String componentName, final StateStorageOperation operation) throws StateStorage.StateStorageException;


  interface ExternalizationSession {
    void setState(@NotNull Storage[] storageSpecs, Object component, final String componentName, Object state) throws StateStorage.StateStorageException;
    void setStateInOldStorage(Object component, final String componentName, Object state) throws StateStorage.StateStorageException;
  }

  interface SaveSession {
    //returns set of component which were changed, null if changes are much more than just component state.
    @Nullable
    Set<String> analyzeExternalChanges(Set<VirtualFile> files);

    List<VirtualFile> getAllStorageFilesToSave() throws StateStorage.StateStorageException;
    void save() throws StateStorage.StateStorageException;

    Set<String> getUsedMacros() throws StateStorage.StateStorageException;

    StateStorage.SaveSession getSaveSession(final String storage);
  }
}
