package com.jetbrains.python.packaging;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;

/**
 * @author yole
 */
public abstract class PyPackageManager {
  public static PyPackageManager getInstance(Sdk sdk) {
    return PyPackageManagers.getInstance().forSdk(sdk);
  }

  public abstract boolean hasPip();
  public abstract void install(String requirementString) throws PyExternalProcessException;
  public abstract void showInstallationError(Project project, String title, String description);
  public abstract void refresh();
}
