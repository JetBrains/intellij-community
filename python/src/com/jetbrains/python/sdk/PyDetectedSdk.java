package com.jetbrains.python.sdk;

import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;

public class PyDetectedSdk extends ProjectJdkImpl {
  public PyDetectedSdk(String name, SdkTypeId sdkType) {
    super(name, sdkType);
    setHomePath(name);
  }

}
