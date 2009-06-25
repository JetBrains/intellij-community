package com.intellij.testFramework;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;

/**
 * @author yole
 */
public interface LightProjectDescriptor {
  ModuleType getModuleType();
  Sdk getSdk();
  void configureModule(Module module, ModifiableRootModel model);
}
