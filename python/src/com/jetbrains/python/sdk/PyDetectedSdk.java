package com.jetbrains.python.sdk;

import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;

public class PyDetectedSdk extends ProjectJdkImpl {
  public PyDetectedSdk(String name) {
    super(name, PythonSdkType.getInstance());
    setHomePath(name);
  }

  @Override
  public String getVersionString() {
    return "";
  }
}
