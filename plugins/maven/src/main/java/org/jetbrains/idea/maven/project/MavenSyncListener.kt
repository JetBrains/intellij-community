// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public interface MavenSyncListener {
  @Topic.AppLevel
  Topic<MavenSyncListener> TOPIC = Topic.create("Maven import notifications", MavenSyncListener.class);

  /**
    called when maven model is collected and IDEA ready to import maven model to workspace model
   */
  default void importModelStarted(@NotNull Project project) { }

  /*
    called upon maven sync started
   */
  default void syncStarted(@NotNull Project project) { }

  /*
    workspace model is commited, project structure is created. Please note, that some processes related to maven could not be completed yet, say facet updating, plugin descriptors could not be ready, etc.
   */
  default void importFinished(@NotNull Project project,
                              @NotNull Collection<MavenProject> importedProjects,
                              @NotNull List<@NotNull Module> newModules) { }
}
