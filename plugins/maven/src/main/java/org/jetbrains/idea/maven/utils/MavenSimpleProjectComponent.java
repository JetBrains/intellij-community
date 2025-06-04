// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils;

import com.intellij.ide.warmup.WarmupStatus;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public abstract class MavenSimpleProjectComponent {
  @ApiStatus.Internal
  public static boolean isNormalProjectInHeadless() {
    return WarmupStatus.InProgress.INSTANCE.equals(WarmupStatus.Companion.currentStatus()) || 
           Boolean.parseBoolean(System.getProperty("maven.default.headless.import", "false"));
  }

  protected final Project myProject;

  protected MavenSimpleProjectComponent(@NotNull Project project) {
    myProject = project;
  }

  protected boolean isNormalProject() {
    var isHeadless = ApplicationManager.getApplication().isHeadlessEnvironment();
    return !MavenUtil.isMavenUnitTestModeEnabled() &&
           (!isHeadless || isNormalProjectInHeadless()) &&
           !isDefault();
  }

  protected boolean isDefault() {
    return myProject.isDefault();
  }

  public Project getProject() {
    return myProject;
  }
}
