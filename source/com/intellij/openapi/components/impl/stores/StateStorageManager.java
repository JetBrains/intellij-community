package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class StateStorageManager {
  private Map<String, String> myMacros = new HashMap<String, String>();
  private Map<String, StateStorage> myStorages = new HashMap<String, StateStorage>();
  private PathMacroSubstitutor myPathMacroManager;
  private String myRootTagName;

  public StateStorageManager(@Nullable final PathMacroSubstitutor pathMacroManager, final String rootTagName) {
    myRootTagName = rootTagName;
    myPathMacroManager = pathMacroManager;
  }

  public synchronized void addMacro(String macro, String expansion) {
    myMacros.put("$" + macro + "$", expansion);
  }

  @Nullable
  public StateStorage getStateStorage(@NotNull final Storage storageSpec) throws StateStorage.StateStorageException {
    final String key = getStorageSpecId(storageSpec);
    if (myStorages.get(key) == null) {
      final StateStorage stateStorage = createStateStorage(storageSpec);
      if (stateStorage == null) return null;
      myStorages.put(key, stateStorage);
    }

    return myStorages.get(key);
  }

  @Nullable
  public StateStorage getFileStateStorage(final String fileName) {
    if (myStorages.get(fileName) == null) {
      final StateStorage stateStorage = createFileStateStorage(fileName);
      if (stateStorage == null) return null;
      myStorages.put(fileName, stateStorage);
    }

    return myStorages.get(fileName);
  }


  @Nullable
  private StateStorage createStateStorage(final Storage storageSpec) throws StateStorage.StateStorageException {
    if (!storageSpec.storageClass().equals(StorageAnnotationsDefaultValues.NullStateStorage.class)) {
      try {
        return storageSpec.storageClass().newInstance();
      }
      catch (InstantiationException e) {
        throw new StateStorage.StateStorageException(e);
      }
      catch (IllegalAccessException e) {
        throw new StateStorage.StateStorageException(e);
      }
    }
    else if (!storageSpec.stateSplitter().equals(StorageAnnotationsDefaultValues.NullStateSplitter.class)) {
      return createDirectoryStateStorage(storageSpec.file(), storageSpec.stateSplitter());
    }
    else return createFileStateStorage(storageSpec.file());
  }

  private static String getStorageSpecId(final Storage storageSpec) {
    if (!storageSpec.storageClass().equals(StorageAnnotationsDefaultValues.NullStateStorage.class)) {
      return storageSpec.storageClass().getName();
    }
    else return storageSpec.file();
  }

  public void clearStateStorage(@NotNull final String file) {
    myStorages.remove(file);
  }

  @Nullable
  private StateStorage createDirectoryStateStorage(final String file, final Class<? extends StateSplitter> splitterClass) throws
                                                                                                                          StateStorage.StateStorageException {
    final String expandedFile = expandMacroses(file);
    if (expandedFile == null) {
      myStorages.put(file, null);
      return null;
    }

    final StateSplitter splitter;

    try {
      splitter = splitterClass.newInstance();
    }
    catch (InstantiationException e) {
      throw new StateStorage.StateStorageException(e);
    }
    catch (IllegalAccessException e) {
      throw new StateStorage.StateStorageException(e);
    }

    return new DirectoryBasedStorage(myPathMacroManager, expandedFile, splitter);
  }

  @Nullable
  private StateStorage createFileStateStorage(@NotNull final String file) {
    String expandedFile = expandMacroses(file);
    if (expandedFile == null) {
      myStorages.put(file, null);
      return null;
    }

    assert expandedFile.indexOf("$") < 0 : "Can't expand all macroses in: " + file;

    return new FileBasedStorage(myPathMacroManager, expandedFile, myRootTagName);
  }

  @Nullable
  private String expandMacroses(final String file) {
    String actualFile = file;

    for (String macro : myMacros.keySet()) {
      final String replacement = myMacros.get(macro);
      if (replacement == null) {
        return null;
      }

      actualFile = StringUtil.replace(actualFile, macro, replacement);
    }

    return actualFile;
  }

  public void save() throws StateStorage.StateStorageException, IOException {
    for (StateStorage storage : myStorages.values()) {
      storage.save();
    }
  }

  public List<VirtualFile> getAllStorageFiles() {
    List<VirtualFile> result = new ArrayList<VirtualFile>();

    for (StateStorage storage : myStorages.values()) {
      result.addAll(storage.getAllStorageFiles());
    }

    return result;
  }

  public List<VirtualFile> getAllStorageFilesToSave() throws StateStorage.StateStorageException {
    List<VirtualFile> result = new ArrayList<VirtualFile>();

    for (StateStorage storage : myStorages.values()) {
      if (storage.needsSave()) {
        result.addAll(storage.getAllStorageFiles());
      }
    }

    return result;
  }
}
