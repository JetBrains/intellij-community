package com.intellij.projectImport;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;

/**
 * @author Vladislav.Kaznacheev
 */
public interface ProjectImportProvider {
  @NonNls ExtensionPointName<ProjectImportProvider> EXTENSION_POINT_NAME = new ExtensionPointName<ProjectImportProvider>("com.intellij.projectImportProvider");

  String getName();

  void doImport(Project currentProject);
}
