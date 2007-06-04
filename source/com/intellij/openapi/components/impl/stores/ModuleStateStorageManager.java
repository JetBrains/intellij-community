package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.StateStorageOperation;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

class ModuleStateStorageManager extends StateStorageManagerImpl {
  @NonNls private static final String ROOT_TAG_NAME = "module";
  private final Module myModule;

  public ModuleStateStorageManager(@Nullable final TrackingPathMacroSubstitutor pathMacroManager, final Module module) {
    super(pathMacroManager, ROOT_TAG_NAME, module, module.getPicoContainer());
    myModule = module;
  }

  protected XmlElementStorage.StorageData createStorageData(String storageSpec) {
    return new ModuleStoreImpl.ModuleFileData(ROOT_TAG_NAME, myModule);
  }

  protected String getOldStorageFilename(Object component, final String componentName, final StateStorageOperation operation) {
    return ModuleStoreImpl.DEFAULT_STATE_STORAGE;
  }
}
