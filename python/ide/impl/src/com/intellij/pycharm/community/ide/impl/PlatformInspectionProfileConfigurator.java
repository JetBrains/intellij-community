// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectConfigurator;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import org.jetbrains.annotations.NotNull;

final class PlatformInspectionProfileConfigurator implements DirectoryProjectConfigurator {
  @Override
  public void configureProject(@NotNull Project project, @NotNull VirtualFile baseDir, @NotNull Ref<Module> moduleRef, boolean isProjectCreatedWithWizard) {
    ProjectInspectionProfileManager.getInstance(project).setRootProfile(null);
  }
}
