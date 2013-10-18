package com.jetbrains.python.newProject;

import com.intellij.openapi.projectRoots.Sdk;

/**
 * Project generation settings selected on the first page of the new project dialog.
 *
 * @author catherine
 */
public class PyNewProjectSettings {
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
