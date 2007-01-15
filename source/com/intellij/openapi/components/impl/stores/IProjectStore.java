package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public interface IProjectStore extends IComponentStore {

  void loadProject() throws IOException, JDOMException, InvalidDataException;
  void saveProject();

  boolean checkVersion();

  @SuppressWarnings({"EmptyMethod"})
  boolean isSavePathsRelative();

  void setProjectFilePath(final String filePath);

  Element saveToXml(final VirtualFile configFile);

  void loadFromXml(final Element root, final String filePath) throws InvalidDataException;
  void loadSavedConfiguration() throws JDOMException, IOException, InvalidDataException;

  void setSavePathsRelative(final boolean b);


  @Nullable
  VirtualFile getProjectBaseDir();

  //------ This methods should be got rid of
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
