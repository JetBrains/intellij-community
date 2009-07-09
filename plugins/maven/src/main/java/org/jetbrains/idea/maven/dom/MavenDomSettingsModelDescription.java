package org.jetbrains.idea.maven.dom;

import org.jetbrains.idea.maven.dom.model.MavenDomSettingsModel;

public class MavenDomSettingsModelDescription extends MavenDomFileDescription<MavenDomSettingsModel> {
  public MavenDomSettingsModelDescription() {
    super(MavenDomSettingsModel.class, "settings");
  }
}
