package org.jetbrains.maven.embedder;

import org.codehaus.plexus.PlexusContainer;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Anchipolevsky
 */
public interface PlexusComponentConfigurator {
  void configureComponents(@NotNull PlexusContainer container);

}
