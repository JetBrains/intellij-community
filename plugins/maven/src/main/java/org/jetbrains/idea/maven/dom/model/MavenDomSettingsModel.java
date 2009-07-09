package org.jetbrains.idea.maven.dom.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;
import org.jetbrains.idea.maven.dom.model.MavenDomProfiles;

public interface MavenDomSettingsModel extends MavenDomElement {
  @NotNull
  MavenDomProfiles getProfiles();
}
