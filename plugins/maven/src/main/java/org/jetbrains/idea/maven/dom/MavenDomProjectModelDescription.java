package org.jetbrains.idea.maven.dom;

import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

public class MavenDomProjectModelDescription extends MavenDomFileDescription<MavenDomProjectModel> {
  public MavenDomProjectModelDescription() {
    super(MavenDomProjectModel.class, "project");
  }
}
