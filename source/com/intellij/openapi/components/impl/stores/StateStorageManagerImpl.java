package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

import java.util.*;

abstract class StateStorageManagerImpl implements StateStorageManager, Disposable {
  private Map<String, String> myMacros = new HashMap<String, String>();
  private Map<String, StateStorage> myStorages = new HashMap<String, StateStorage>();
  private TrackingPathMacroSubstitutor myPathMacroSubstitutor;
  private String myRootTagName;
  private Object mySession;
  private PicoContainer myPicoContainer;

  public StateStorageManagerImpl(
    @Nullable final TrackingPathMacroSubstitutor pathMacroSubstitutor,
    final String rootTagName,
    Disposable parentDisposable,
    PicoContainer picoContainer) {
    myPicoContainer = picoContainer;
    myRootTagName = rootTagName;
    myPathMacroSubstitutor = pathMacroSubstitutor;
    Disposer.register(parentDisposable, this);
  }

  public synchronized void addMacro(String macro, String expansion) {
    myMacros.put("$" + macro + "$", expansion);
  }

  @Nullable
  public StateStorage getStateStorage(@NotNull final Storage storageSpec) throws StateStorage.StateStorageException {
    final String key = getStorageSpecId(storageSpec);
    return getStateStorage(storageSpec, key);
  }

  @Nullable
  private StateStorage getStateStorage(final Storage storageSpec, final String key) throws StateStorage.StateStorageException {
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

    throw new UnsupportedOperationException();
    //return new DirectoryBasedStorage(myPathMacroSubstitutor, expandedFile, splitter, this);
  }

  @Nullable
  private StateStorage createFileStateStorage(@NotNull final String fileSpec) {
    String expandedFile = expandMacroses(fileSpec);
    if (expandedFile == null) {
      myStorages.put(fileSpec, null);
      return null;
    }

    assert expandedFile.indexOf("$") < 0 : "Can't expand all macroses in: " + fileSpec;

    return new FileBasedStorage(getMacroSubstitutor(fileSpec), expandedFile, myRootTagName, this, myPicoContainer) {
      protected StorageData createStorageData() {
        return StateStorageManagerImpl.this.createStorageData(fileSpec);
      }
    };
  }

  protected TrackingPathMacroSubstitutor getMacroSubstitutor(@NotNull final String fileSpec) {
    return myPathMacroSubstitutor;
  }


  protected abstract XmlElementStorage.StorageData createStorageData(String storageSpec);

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

      StateStorage stateStorage;             
      for (Storage storageSpec : storageSpecs) {
        stateStorage = getStateStorage(storageSpec);
        if (stateStorage == null) continue;

        final StateStorage.ExternalizationSession extSession = myCompoundExternalizationSession.getExternalizationSession(stateStorage);
        extSession.setState(component, componentName, state, storageSpec);
      }
    }

    public void setStateInOldStorage(Object component, final String componentName, Object state) throws StateStorage.StateStorageException {
      assert mySession == this;
      StateStorage stateStorage = getOldStorage(component, componentName, StateStorageOperation.WRITE);
      myCompoundExternalizationSession.getExternalizationSession(stateStorage).setState(component, componentName, state, null);
    }
  }

  @Nullable
  public StateStorage getOldStorage(Object component, final String componentName, final StateStorageOperation operation) throws StateStorage.StateStorageException {
    return getFileStateStorage(getOldStorageSpec(component, componentName, operation));
  }

  protected abstract String getOldStorageSpec(Object component, final String componentName, final StateStorageOperation operation)
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

    public StateStorage.SaveSession getSaveSession(final String storage) {
      final StateStorage stateStorage = myStorages.get(storage);
      assert stateStorage != null;
      return myCompoundSaveSession.getSaveSession(stateStorage);
    }

    public void finishSave() {
      assert mySession == this;
      myCompoundSaveSession.finishSave();
    }

    //returns set of component which were changed, null if changes are much more than just component state.
    @Nullable
    public Set<String> analyzeExternalChanges(final Set<VirtualFile> changedFiles) {
      Set<String> result = new HashSet<String>();

      nextSorage: for (StateStorage storage : myStorages.values()) {
        final List<VirtualFile> virtualFiles = storage.getAllStorageFiles();

        for (VirtualFile virtualFile : virtualFiles) {
          if (changedFiles.contains(virtualFile)) {
            final Set<String> s = myCompoundSaveSession.getSaveSession(storage).analyzeExternalChanges(changedFiles);

            if (s == null) return null;
            result.addAll(s);

            continue nextSorage;
          }
        }
      }

      return result;
    }
  }

  public void dispose() {
  }

  public void reload(final Set<VirtualFile> changedFiles, final Set<String> changedComponents) throws StateStorage.StateStorageException {
    nextSorage: for (StateStorage storage : myStorages.values()) {
      final List<VirtualFile> virtualFiles = storage.getAllStorageFiles();

      for (VirtualFile virtualFile : virtualFiles) {
        if (changedFiles.contains(virtualFile)) {
          storage.reload(changedComponents);
          continue nextSorage;
        }
      }
    }
  }
}
