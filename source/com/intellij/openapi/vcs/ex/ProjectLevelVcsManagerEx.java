package com.intellij.openapi.vcs.ex;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.ui.content.ContentManager;

public abstract class ProjectLevelVcsManagerEx extends ProjectLevelVcsManager {
  public static ProjectLevelVcsManagerEx getInstanceEx(Project project) {
    return (ProjectLevelVcsManagerEx)project.getComponent(ProjectLevelVcsManager.class);
  }

  public abstract LineStatusTracker getLineStatusTracker(Document document);

  public abstract LineStatusTracker setUpToDateContent(Document document,
                                                       String lastUpToDateContent);

  public abstract ContentManager getContentManager();
}