// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.facet;

import com.intellij.openapi.projectRoots.Sdk;


public class PythonFacetSettings {
  protected Sdk mySdk;

  public Sdk getSdk() {
    return mySdk;
  }

  public void setSdk(Sdk sdk) {
    mySdk = sdk;
  }
}
