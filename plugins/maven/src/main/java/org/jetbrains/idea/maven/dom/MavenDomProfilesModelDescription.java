package org.jetbrains.idea.maven.dom;

import org.jetbrains.idea.maven.dom.model.MavenDomProfiles;

public class MavenDomProfilesModelDescription extends MavenDomFileDescription<MavenDomProfiles> {
  public MavenDomProfilesModelDescription() {
    super(MavenDomProfiles.class, "profiles");
  }
}
