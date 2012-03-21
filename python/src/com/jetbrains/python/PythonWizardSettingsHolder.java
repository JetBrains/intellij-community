package com.jetbrains.python;

import com.intellij.openapi.projectRoots.Sdk;

/**
 * User : catherine
 */
public class PythonWizardSettingsHolder {
  private Sdk mySdk;
  private boolean myInstallDjango;

  public Sdk getSdk() {
    return mySdk;
  }

  public void setSdk(Sdk sdk) {
    mySdk = sdk;
  }

  public void setInstallDjango(final boolean installDjango) {
    myInstallDjango = installDjango;
  }

  public boolean installDjango() {
    return myInstallDjango;
  }
}
