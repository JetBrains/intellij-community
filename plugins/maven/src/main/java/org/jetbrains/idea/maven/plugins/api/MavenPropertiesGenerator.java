package org.jetbrains.idea.maven.plugins.api;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenPlugin;

import java.util.Properties;

/**
 * @author Sergey Evdokimov
 */
public abstract class MavenPropertiesGenerator {

  public abstract void generate(@NotNull Properties modelProperties,
                                @Nullable String goal,
                                @NotNull MavenPlugin plugin,
                                @Nullable Element cfgElement);

}
