package org.jetbrains.idea.maven.dom;

import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.converters.MavenPropertyReferenceInjector;

public class MavenDomProjectModelDescription extends MavenDomFileDescription<MavenDomProjectModel> {
  public MavenDomProjectModelDescription() {
    super(MavenDomProjectModel.class, "project");
  }

  @Override
  protected void initializeFileDescription() {
    super.initializeFileDescription();
    registerReferenceInjector(new MavenPropertyReferenceInjector());
  }
}
