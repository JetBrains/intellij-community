package org.jetbrains.idea.maven.dom.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

import java.util.List;

public interface MavenDomMirrors extends MavenDomElement {
  @NotNull
  List<MavenDomMirror> getMirrors();
}
