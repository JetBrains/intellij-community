package org.jetbrains.idea.maven.dom;

import org.jetbrains.idea.maven.dom.model.MavenDomOldProfilesModel;

public class MavenDomOldProfilesModelDescription extends MavenDomFileDescription<MavenDomOldProfilesModel> {
  public MavenDomOldProfilesModelDescription() {
    super(MavenDomOldProfilesModel.class, "profiles");
  }
}
