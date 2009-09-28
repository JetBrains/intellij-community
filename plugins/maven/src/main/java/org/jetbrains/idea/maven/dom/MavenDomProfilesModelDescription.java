package org.jetbrains.idea.maven.dom;

import org.jetbrains.idea.maven.dom.model.MavenDomProfilesModel;

public class MavenDomProfilesModelDescription extends MavenDomFileDescription<MavenDomProfilesModel> {
  public MavenDomProfilesModelDescription() {
    super(MavenDomProfilesModel.class, "profilesXml");
  }
}