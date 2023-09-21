// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
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

  default void importStarted() { }

  void importFinished(@NotNull Collection<MavenProject> importedProjects, @NotNull List<@NotNull Module> newModules);

  default void pomReadingStarted() { }

  default void pomReadingFinished() { }

  default void pluginResolutionStarted() { }

  default void pluginResolutionFinished() { }

  default void artifactDownloadingStarted() { }

  default void artifactDownloadingFinished() { }
}
