package org.jetbrains.idea.maven.dom.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

public interface MavenDomPluginModel extends MavenDomElement {
  @NotNull
  MavenDomMojos getMojos();
}
