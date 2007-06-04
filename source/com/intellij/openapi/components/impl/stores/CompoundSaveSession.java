package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.*;

/**
 * @author mike
 */
public class CompoundSaveSession {
  private Map<StateStorage, StateStorage.SaveSession> mySaveSessions = new HashMap<StateStorage, StateStorage.SaveSession>();

  public CompoundSaveSession(final CompoundExternalizationSession compoundExternalizationSession) {
    final Collection<StateStorage> stateStorages = compoundExternalizationSession.getStateStorages();

    for (StateStorage stateStorage : stateStorages) {
      mySaveSessions.put(stateStorage, stateStorage.startSave(compoundExternalizationSession.getExternalizationSession(stateStorage)));
    }
  }

  public List<VirtualFile> getAllStorageFilesToSave() throws StateStorage.StateStorageException {
    List<VirtualFile> result = new ArrayList<VirtualFile>();

    for (StateStorage stateStorage : mySaveSessions.keySet()) {
      final StateStorage.SaveSession saveSession = mySaveSessions.get(stateStorage);

      if (saveSession.needsSave()) result.addAll(stateStorage.getAllStorageFiles());
    }

    return result;
  }

  public void save() throws StateStorage.StateStorageException {
    for (StateStorage.SaveSession saveSession : mySaveSessions.values()) {
      saveSession.save();
    }
  }

  public Set<String> getUsedMacros() throws StateStorage.StateStorageException {
    Set<String> usedMacros = new HashSet<String>();

    for (StateStorage.SaveSession saveSession : mySaveSessions.values()) {
      usedMacros.addAll(saveSession.getUsedMacros());
    }

    return usedMacros;
  }

  public void finishSave() {
    for (StateStorage stateStorage : mySaveSessions.keySet()) {
      final StateStorage.SaveSession saveSession = mySaveSessions.get(stateStorage);

      stateStorage.finishSave(saveSession);
    }
  }

  public StateStorage.SaveSession getSaveSession(final StateStorage storage) {
    return mySaveSessions.get(storage);
  }
}
