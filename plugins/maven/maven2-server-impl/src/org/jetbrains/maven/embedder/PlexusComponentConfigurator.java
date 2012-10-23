package org.jetbrains.maven.embedder;

import org.codehaus.plexus.PlexusContainer;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Anchipolevsky
 *         Date: 21.09.2009
 */
public interface PlexusComponentConfigurator {
  void configureComponents(@NotNull PlexusContainer container);

}
