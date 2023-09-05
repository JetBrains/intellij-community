// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils;

import com.intellij.ide.warmup.WarmupStatus;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class MavenSimpleProjectComponent {
  protected final Project myProject;

  protected MavenSimpleProjectComponent(@NotNull Project project) {
    myProject = project;
  }

  protected boolean isNormalProject() {
    return !MavenUtil.isMavenUnitTestModeEnabled() && !isHeadless() && !isDefault();
  }

  protected boolean isHeadless() {
    return ApplicationManager.getApplication().isHeadlessEnvironment() &&
           !WarmupStatus.InProgress.INSTANCE.equals(WarmupStatus.Companion.currentStatus(ApplicationManager.getApplication()));
  }

  protected boolean isDefault() {
    return myProject.isDefault();
  }

  public Project getProject() {
    return myProject;
  }
}
