// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class MavenImportListenerBridge implements MavenSyncListener {

  @Override
  public void importModelStarted(@NotNull Project project) {
    project.getMessageBus().syncPublisher(MavenImportListener.TOPIC).importStarted();
  }

  @Override
  public void importFinished(@NotNull Project project,
                             @NotNull Collection<MavenProject> importedProjects,
                             @NotNull List<@NotNull Module> newModules) {
    project.getMessageBus().syncPublisher(MavenImportListener.TOPIC).importFinished(importedProjects, newModules);
  }
}
