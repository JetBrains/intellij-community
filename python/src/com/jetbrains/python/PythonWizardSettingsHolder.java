package com.jetbrains.python;

import com.intellij.openapi.projectRoots.Sdk;

/**
 * User : catherine
 */
public class PythonWizardSettingsHolder {
  private Sdk mySdk;
  private boolean myInstallFramework;

  public Sdk getSdk() {
    return mySdk;
  }

  public void setSdk(Sdk sdk) {
    mySdk = sdk;
  }

  public void setInstallFramework(final boolean installFramework) {
    myInstallFramework = installFramework;
  }

  public boolean installFramework() {
    return myInstallFramework;
  }
}
