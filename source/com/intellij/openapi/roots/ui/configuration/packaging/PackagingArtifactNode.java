package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.openapi.deployment.ContainerElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class PackagingArtifactNode extends PackagingTreeNode {
  private PackagingArtifact myArtifact;

  PackagingArtifactNode(final PackagingArtifact artifact, PackagingArtifact owner) {
    super(owner);
    myArtifact = artifact;
  }

  public PackagingArtifact getArtifact() {
    return myArtifact;
  }

  @NotNull
  public String getOutputFileName() {
    return myArtifact.getOutputFileName();
  }

  public double getWeight() {
    return PackagingNodeWeights.ARTIFACT;
  }

  public void render(final ColoredTreeCellRenderer renderer) {
    myArtifact.render(renderer, getMainAttributes(), getCommentAttributes());
  }

  public boolean canNavigate() {
    return true;
  }

  public void navigate(final ModuleStructureConfigurable configurable) {
    myArtifact.navigate(configurable, null);
  }

  public Object getSourceObject() {
    return null;
  }

  @Override
  public ContainerElement getContainerElement() {
    return myArtifact.getContainerElement();
  }
}
