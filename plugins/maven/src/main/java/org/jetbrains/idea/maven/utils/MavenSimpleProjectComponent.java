// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class MavenSimpleProjectComponent {
  protected final Project myProject;

  protected MavenSimpleProjectComponent(@NotNull Project project) {
    myProject = project;
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

  public Project getProject() {
    return myProject;
  }
}
