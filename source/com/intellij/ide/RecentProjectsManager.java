package com.intellij.ide;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.messages.MessageBus;

import java.io.File;

@State(
  name = "RecentProjectsManager",
  storages = {
    @Storage(
      id ="other",
      file = "$APP_CONFIG$/other.xml"
    )}
)
public class RecentProjectsManager extends RecentProjectsManagerBase {
  public RecentProjectsManager(final ProjectManager projectManager, final MessageBus messageBus) {
    super(projectManager, messageBus);
  }

  protected String getProjectPath(Project project) {
    return project.getLocation().replace('/', File.separatorChar);
  }

  protected void doOpenProject(final String projectPath, Project projectToClose) {
    ProjectUtil.openProject(projectPath, projectToClose, false);
  }
}
