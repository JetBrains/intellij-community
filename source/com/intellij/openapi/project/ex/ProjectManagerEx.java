package com.intellij.openapi.project.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.JDOMException;

import java.io.IOException;

/**
 *
 */
public abstract class ProjectManagerEx extends ProjectManager {
  public static ProjectManagerEx getInstanceEx() {
    return (ProjectManagerEx)ApplicationManager.getApplication().getComponent(ProjectManager.class);
  }

  public abstract Project newProject(String filePath, boolean useDefaultProjectSettings, boolean isDummy);

  public abstract Project loadProject(String filePath) throws IOException, JDOMException, InvalidDataException;

  public abstract boolean openProject(Project project);

  public abstract boolean isProjectOpened(Project project);

  public abstract boolean canClose(Project project);

  public abstract void saveChangedProjectFile(VirtualFile file);

  public abstract boolean isFileSavedToBeReloaded(VirtualFile file);

  public abstract void blockReloadingProjectOnExternalChanges();
  public abstract void unblockReloadingProjectOnExternalChanges();
}
