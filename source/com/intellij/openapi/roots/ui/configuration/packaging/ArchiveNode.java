package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
class ArchiveNode extends PackagingTreeNode {
  private String myArchiveName;

  ArchiveNode(final String archiveName, final PackagingArtifact owner) {
    super(owner);
    myArchiveName = archiveName;
  }

  @NotNull
  protected String getOutputFileName() {
    return myArchiveName;
  }

  public double getWeight() {
    return PackagingNodeWeights.ARCHIVE;
  }

  public void render(final ColoredTreeCellRenderer renderer) {
    renderer.setIcon(Icons.JAR_ICON);
    renderer.append(myArchiveName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public boolean canNavigate() {
    return false;
  }

  public void navigate(final ModuleStructureConfigurable configurable) {
  }

  public Object getSourceObject() {
    return null;
  }
}
