package com.jetbrains.python;

import com.intellij.openapi.projectRoots.Sdk;

/**
 * User : catherine
 */
public class PythonWizardSettingsHolder {
  private Sdk mySdk;

  public Sdk getSdk() {
    return mySdk;
  }

  public void setSdk(Sdk sdk) {
    mySdk = sdk;
  }
}
