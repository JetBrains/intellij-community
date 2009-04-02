package org.jetbrains.idea.maven.dom.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

import java.util.List;

public interface MavenDomParameters extends MavenDomElement {
  @NotNull
  List<MavenDomParameter> getParameters();
}
