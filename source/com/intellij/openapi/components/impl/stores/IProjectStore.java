package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Set;

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

  void loadProjectFromTemplate(ProjectImpl project);

  @NotNull
  String getProjectFileName();

  @NotNull
  String getProjectFilePath();

  Set<String> getMacroTrackingSet();
}
