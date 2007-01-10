package com.intellij.openapi.components.impl.stores;

import com.intellij.application.options.ExpandMacroToPathMap;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public interface IProjectStore extends IComponentStore {
  void setProject(ProjectImpl project);

  void loadProject(final ProjectManagerImpl projectManager) throws IOException, JDOMException, InvalidDataException;
  void saveProject();

  boolean checkVersion();

  ReplacePathToMacroMap getMacroReplacements();
  ExpandMacroToPathMap getExpandMacroReplacements();

  @SuppressWarnings({"EmptyMethod"})
  boolean isSavePathsRelative();

  void setProjectFilePath(final String filePath);

  @Nullable
  VirtualFile getProjectFile();

  @Nullable
  VirtualFile getWorkspaceFile();

  void loadProjectFromTemplate(Project project);

  @NotNull
  String getProjectFileName();

  @NotNull
  String getProjectFilePath();
}
