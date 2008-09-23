package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
class DirectoryNode extends PackagingTreeNode {
  private String myDirectoryName;

  DirectoryNode(final String directoryName, PackagingArtifact owner) {
    super(owner);
    myDirectoryName = directoryName;
  }

  @NotNull
  public String getOutputFileName() {
    return myDirectoryName;
  }

  public double getWeight() {
    return PackagingNodeWeights.DIRECTORY;
  }

  public void render(@NotNull final ColoredTreeCellRenderer renderer) {
    renderer.setIcon(Icons.FOLDER_ICON);
    renderer.append(myDirectoryName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
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
