package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.StateStorageOperation;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import org.jetbrains.annotations.Nullable;

class ModuleStateStorageManager extends StateStorageManagerImpl {
  public ModuleStateStorageManager(@Nullable final TrackingPathMacroSubstitutor pathMacroManager) {
    super(pathMacroManager, "module");
  }

  protected String getOldStorageFilename(Object component, final String componentName, final StateStorageOperation operation) {
    return ModuleStoreImpl.DEFAULT_STATE_STORAGE;
  }
}
