package com.jetbrains.python.packaging;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;

import java.awt.*;

/**
 * @author yole
 */
public abstract class PyPackageManager {
  public static PyPackageManager getInstance(Sdk sdk) {
    return PyPackageManagers.getInstance().forSdk(sdk);
  }

  /**
   * Returns true if pip is installed for the specific interpreter; returns false if pip is not
   * installed or if it is not currently known whether it's installed (e.g. for a remote interpreter).
   *
   * @return true if pip is known to be installed, false otherwise.
   */
  public abstract boolean hasPip();
  public abstract void install(String requirementString) throws PyExternalProcessException;
  public abstract void showInstallationError(Project project, String title, String description);
  public abstract void showInstallationError(Component owner, String title, String description);
  public abstract void refresh();
}
