package com.intellij.projectImport;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Vladislav.Kaznacheev
 */
public interface ProjectImportProvider {
  @NonNls ExtensionPointName<ProjectImportProvider> EXTENSION_POINT_NAME = new ExtensionPointName<ProjectImportProvider>("com.intellij.projectImportProvider");

  String getName();

  @Nullable
  Icon getIcon(VirtualFile file, boolean open);

  void doImport(Project currentProject, boolean forceOpenInNewFrame);

  boolean canOpenProject(VirtualFile file);

  @Nullable
  Project doOpenProject(VirtualFile file, Project projectToClose, boolean forceOpenInNewFrame);
}
