package com.intellij.openapi.module.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModulePointer;

/**
 * @author dsl
 */
public class ModulePointerImpl implements ModulePointer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.module.impl.ModulePointerImpl");
  private Module myModule;
  private String myModuleName;

  ModulePointerImpl(Module module) {
    myModule = module;
    myModuleName = null;
  }

  ModulePointerImpl(String name) {
    myModule = null;
    myModuleName = name;
  }

  public Module getModule() {
    return myModule;
  }

  public String getModuleName() {
    if (myModule != null) {
      return myModule.getName();
    }
    else {
      return myModuleName;
    }
  }

  void moduleAdded(Module module) {
    LOG.assertTrue(myModule == null);
    LOG.assertTrue(myModuleName.equals(module.getName()));
    myModuleName = null;
    myModule = module;
  }

  void moduleRemoved(Module module) {
    LOG.assertTrue(myModule == module);
    myModuleName = myModule.getName();
    myModule = null;
  }

}
