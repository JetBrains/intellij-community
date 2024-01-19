// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public interface MavenImportListener {
  @Topic.ProjectLevel
  Topic<MavenImportListener> TOPIC = Topic.create("Maven import notifications", MavenImportListener.class);

  /**
   * consider usage {@link MavenSyncListener#importStarted(com.intellij.openapi.project.Project) application level listener} instead
   */
  default void importStarted() { }

  /**
   * consider usage {@link MavenSyncListener#importFinished(Project, Collection, List) application level listener} instead
   */
  void importFinished(@NotNull Collection<MavenProject> importedProjects, @NotNull List<@NotNull Module> newModules);

  default void pomReadingStarted() { }

  default void pomReadingFinished() { }

  default void pluginResolutionStarted() { }

  default void pluginResolutionFinished() { }

  default void dependencyResolutionStarted(@NotNull Collection<@NotNull MavenProject> mavenIds) { }

  default void dependencyResolutionFinished(Collection<MavenProject> mavenIds) { }

  default void artifactDownloadingScheduled() { }

  default void artifactDownloadingStarted() { }

  default void artifactDownloadingFinished() { }
}
