package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.openapi.deployment.ContainerElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public abstract class PackagingTreeNode extends DefaultMutableTreeNode {
  private final PackagingArtifact myOwner;

  protected PackagingTreeNode(final @Nullable PackagingArtifact owner) {
    myOwner = owner;
  }

  @NotNull
  protected abstract String getOutputFileName();

  public String getSearchName() {
    return getOutputFileName();
  }

  public abstract void render(ColoredTreeCellRenderer renderer);

  public abstract boolean canNavigate();

  public abstract void navigate(ModuleStructureConfigurable configurable);

  @Nullable
  public abstract Object getSourceObject();

  @Nullable
  public PackagingArtifact getOwner() {
    return myOwner;
  }

  @Nullable
  public ContainerElement getContainerElement() {
    return null;
  }

  @Nullable
  public PackagingTreeNode findChildByName(final @NotNull String outputFileName) {
    for (int i = 0; i < getChildCount(); i++) {
      PackagingTreeNode node = (PackagingTreeNode)getChildAt(i);
      if (node.getOutputFileName().equals(outputFileName)) {
        return node;
      }
    }
    return null;
  }

  @Override
  public PackagingTreeNode getParent() {
    return (PackagingTreeNode)super.getParent();
  }

  @NotNull 
  public List<PackagingTreeNode> getChildren() {
    List<PackagingTreeNode> children = new ArrayList<PackagingTreeNode>(getChildCount());
    for (int i = 0; i < getChildCount(); i++) {
      children.add((PackagingTreeNode)getChildAt(i));
    }
    return children;
  }

  protected SimpleTextAttributes getMainAttributes() {
    return !belongsToIncludedArtifact() ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAY_ATTRIBUTES;
  }

  protected SimpleTextAttributes getCommentAttributes() {
    return !belongsToIncludedArtifact() ? SimpleTextAttributes.GRAY_ATTRIBUTES : SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES;
  }

  protected boolean belongsToIncludedArtifact() {
    if (myOwner == null) return false;

    PackagingTreeNode parent = getParent();
    while (parent != null) {
      if (parent instanceof PackagingArtifactNode && myOwner.equals(((PackagingArtifactNode)parent).getArtifact())) {
        return false;
      }
      parent = parent.getParent();
    }
    return true;
  }
}
