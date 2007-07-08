package org.jetbrains.idea.maven.repo;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.MavenId;

import java.util.Collection;

/**
 * @author Vladislav.Kaznacheev
 */
public interface MavenRepository {
  @Nullable
  PluginDocument loadPlugin(final MavenId mavenId);

  void dropCache();

  @NotNull
  Collection<PluginDocument> choosePlugins();
}
