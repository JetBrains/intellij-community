package com.jetbrains.python.facet;

import com.intellij.openapi.projectRoots.Sdk;

/**
 * @author yole
 */
public class PythonFacetSettings {
  protected Sdk mySdk;

  public Sdk getSdk() {
    return mySdk;
  }

  public void setSdk(Sdk sdk) {
    mySdk = sdk;
  }
}
