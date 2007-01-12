package com.intellij.openapi.components.impl.stores;

import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public interface IProjectStore extends IComponentStore {

  void loadProject(final ProjectManagerImpl projectManager) throws IOException, JDOMException, InvalidDataException;
  void saveProject();

  boolean checkVersion();

  //todo: inline
  ReplacePathToMacroMap getMacroReplacements();
  //todo: inline
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
