package org.jetbrains.idea.maven.repo;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Vladislav.Kaznacheev
 */
public interface MavenRepository {
  @Nullable
  PluginDocument loadPlugin(String groupId, String artifactId, String version);

  Collection<PluginDocument> choosePlugins();
}
