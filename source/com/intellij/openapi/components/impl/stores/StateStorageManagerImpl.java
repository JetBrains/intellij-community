package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

abstract class StateStorageManagerImpl implements StateStorageManager {
  private Map<String, String> myMacros = new HashMap<String, String>();
  private Map<String, StateStorage> myStorages = new HashMap<String, StateStorage>();
  private TrackingPathMacroSubstitutor myPathMacroSubstitutor;
  private String myRootTagName;
  private Object mySession;

  public StateStorageManagerImpl(@Nullable final TrackingPathMacroSubstitutor pathMacroSubstitutor, final String rootTagName) {
    myRootTagName = rootTagName;
    myPathMacroSubstitutor = pathMacroSubstitutor;
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
    else {
      return createFileStateStorage(storageSpec.file());
    }
  }

  private static String getStorageSpecId(final Storage storageSpec) {
    if (!storageSpec.storageClass().equals(StorageAnnotationsDefaultValues.NullStateStorage.class)) {
      return storageSpec.storageClass().getName();
    }
    else {
      return storageSpec.file();
    }
  }

  public void clearStateStorage(@NotNull final String file) {
    myStorages.remove(file);
  }

  @Nullable
  private StateStorage createDirectoryStateStorage(final String file, final Class<? extends StateSplitter> splitterClass)
    throws StateStorage.StateStorageException {
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

    return new DirectoryBasedStorage(myPathMacroSubstitutor, expandedFile, splitter);
  }

  @Nullable
  private StateStorage createFileStateStorage(@NotNull final String file) {
    String expandedFile = expandMacroses(file);
    if (expandedFile == null) {
      myStorages.put(file, null);
      return null;
    }

    assert expandedFile.indexOf("$") < 0 : "Can't expand all macroses in: " + file;

    return new FileBasedStorage(myPathMacroSubstitutor, expandedFile, myRootTagName);
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

  public List<VirtualFile> getAllStorageFiles() {
    List<VirtualFile> result = new ArrayList<VirtualFile>();

    for (StateStorage storage : myStorages.values()) {
      result.addAll(storage.getAllStorageFiles());
    }

    return result;
  }

  public ExternalizationSession startExternalization() {
    assert mySession == null;

    ExternalizationSession session = new MyExternalizationSession();

    mySession = session;

    return session;
  }

  public SaveSession startSave(final ExternalizationSession externalizationSession) throws StateStorage.StateStorageException {
    assert mySession == externalizationSession;

    SaveSession session = createSaveSession(externalizationSession);

    mySession = session;

    return session;
  }

  protected MySaveSession createSaveSession(final ExternalizationSession externalizationSession) throws StateStorage.StateStorageException {
    return new MySaveSession((MyExternalizationSession)externalizationSession);
  }

  public void finishSave(final SaveSession saveSession) {
    assert mySession == saveSession;

    ((MySaveSession)saveSession).finishSave();

    mySession = null;
  }

  protected class MyExternalizationSession implements ExternalizationSession {
    CompoundExternalizationSession myCompoundExternalizationSession = new CompoundExternalizationSession();

    public void setState(@NotNull final Storage[] storageSpecs, final Object component, final String componentName, final Object state)
      throws StateStorage.StateStorageException {
      assert mySession == this;

      StateStorage stateStorage = null;
      for (Storage storageSpec : storageSpecs) {
        stateStorage = getStateStorage(storageSpec);
        if (stateStorage != null) break;

      }
      if (stateStorage != null) {
        myCompoundExternalizationSession.getExternalizationSession(stateStorage).setState(component, componentName, state);
      }
    }

    public void setStateInOldStorage(Object component, final String componentName, Object state) throws StateStorage.StateStorageException {
      assert mySession == this;
      StateStorage stateStorage = getOldStorage(component, componentName, StateStorageOperation.WRITE);
      myCompoundExternalizationSession.getExternalizationSession(stateStorage).setState(component, componentName, state);
    }
  }

  @Nullable
  public StateStorage getOldStorage(Object component, final String componentName, final StateStorageOperation operation) throws StateStorage.StateStorageException {
    return getFileStateStorage(getOldStorageFilename(component, componentName, operation));
  }

  protected abstract String getOldStorageFilename(Object component, final String componentName, final StateStorageOperation operation)
    throws StateStorage.StateStorageException;

  protected class MySaveSession implements SaveSession {
    CompoundSaveSession myCompoundSaveSession;

    public MySaveSession(final MyExternalizationSession externalizationSession) {
      myCompoundSaveSession = new CompoundSaveSession(externalizationSession.myCompoundExternalizationSession);
    }

    public List<VirtualFile> getAllStorageFilesToSave() throws StateStorage.StateStorageException {
      assert mySession == this;
      return myCompoundSaveSession.getAllStorageFilesToSave();
    }

    public void save() throws StateStorage.StateStorageException {
      assert mySession == this;
      myCompoundSaveSession.save();
    }

    public Set<String> getUsedMacros() throws StateStorage.StateStorageException {
      assert mySession == this;
      return myCompoundSaveSession.getUsedMacros();
    }

    public void finishSave() {
      assert mySession == this;
      myCompoundSaveSession.finishSave();
    }
  }
}
