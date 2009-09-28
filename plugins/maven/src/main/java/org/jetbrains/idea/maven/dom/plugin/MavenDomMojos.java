package org.jetbrains.idea.maven.dom.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

import java.util.List;

public interface MavenDomMojos extends MavenDomElement {
  @NotNull
  List<MavenDomMojo> getMojos();
}
