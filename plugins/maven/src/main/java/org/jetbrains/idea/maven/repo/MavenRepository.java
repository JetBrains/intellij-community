package org.jetbrains.idea.maven.repo;

import java.util.Collection;

/**
 * @author Vladislav.Kaznacheev
 */
public interface MavenRepository {
  PluginDocument loadPlugin(String groupId, String artifactId, String version);

  Collection<PluginDocument> choosePlugins();
}
