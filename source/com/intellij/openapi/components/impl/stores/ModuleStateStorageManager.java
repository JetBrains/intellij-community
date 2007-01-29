package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.PathMacroSubstitutor;
import com.intellij.openapi.module.impl.ModuleImpl;
import org.jetbrains.annotations.Nullable;

class ModuleStateStorageManager extends StateStorageManager {
  public ModuleStateStorageManager(@Nullable final PathMacroSubstitutor pathMacroManager, final ModuleImpl module) {
    super(pathMacroManager);
  }
}
