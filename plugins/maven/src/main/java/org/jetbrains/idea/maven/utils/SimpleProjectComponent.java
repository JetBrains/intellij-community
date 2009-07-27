package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class SimpleProjectComponent implements ProjectComponent {
  protected final Project myProject;

  protected SimpleProjectComponent(Project project) {
    myProject = project;
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return getClass().getSimpleName();
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  protected boolean isNormalProject() {
    return !isUnitTestMode() && !isHeadless() && !isDefault();
  }

  protected boolean isNoBackgroundMode() {
    return MavenUtil.isNoBackgroundMode();
  }

  protected boolean isUnitTestMode() {
    return ApplicationManager.getApplication().isUnitTestMode();
  }

  protected boolean isHeadless() {
    return ApplicationManager.getApplication().isHeadlessEnvironment();
  }

  protected boolean isDefault() {
    return myProject.isDefault();
  }
}
