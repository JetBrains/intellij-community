package com.jetbrains.python.packaging;

import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public interface PyPackageManager {
  void install(String requirementString) throws PyExternalProcessException;
  void showInstallationError(Project project, String title, String description);
}
