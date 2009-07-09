package org.jetbrains.idea.maven.dom.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

public interface MavenDomProfilesModel extends MavenDomElement {
  @NotNull
  MavenDomProfiles getProfiles();
}
