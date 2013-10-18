package com.jetbrains.python;

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.impl.ModuleTypeManagerImpl;

/**
 * @author yole
 */
public class PythonModuleTypeManager extends ModuleTypeManagerImpl {
  @Override
  public ModuleType getDefaultModuleType() {
    return new PlatformPythonModuleType();
  }
}
