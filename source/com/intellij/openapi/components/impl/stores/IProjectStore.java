package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public interface IProjectStore extends IComponentStore {


  boolean checkVersion();

  @SuppressWarnings({"EmptyMethod"})
  boolean isSavePathsRelative();

  void setProjectFilePath(final String filePath);

  void setSavePathsRelative(final boolean b);


  @Nullable
  VirtualFile getProjectBaseDir();

  //------ This methods should be got rid of
  void loadProject() throws IOException, JDOMException, InvalidDataException;

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
