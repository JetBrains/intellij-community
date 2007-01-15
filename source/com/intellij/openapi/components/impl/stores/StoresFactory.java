package com.intellij.openapi.components.impl.stores;

public class StoresFactory {
  private StoresFactory() {
  }

  public static Class getModuleStoreClass() {
    return ModuleStoreImpl.class;
  }

  public static Class getProjectStoreClass() {
    return ProjectStoreImpl.class;
  }

  public static Class getApplicationStoreClass() {
    return ApplicationStoreImpl.class;
  }
}
