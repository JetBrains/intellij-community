package com.intellij.openapi.project.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.InvalidDataException;
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

  public abstract void openProject(Project project);

  public abstract boolean closeProject(Project project);

  public abstract boolean isProjectOpened(Project project);

  public abstract boolean canClose(Project project);
}
