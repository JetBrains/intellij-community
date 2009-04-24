package com.intellij.openapi.roots.ui.configuration.artifacts;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
 */
public abstract class ArtifactsTreeNode extends DefaultMutableTreeNode {
  public List<? extends ArtifactsTreeNode> getChildren() {
    final List<ArtifactsTreeNode> nodes = new ArrayList<ArtifactsTreeNode>(getChildCount());
    for (int i = 0; i < getChildCount(); i++) {
      nodes.add((PackagingElementNode)getChildAt(i));
    }
    return nodes;
  }

  @Nullable
  public ArtifactsTreeNode findChildByName(final @NotNull String name) {
    for (int i = 0; i < getChildCount(); i++) {
      ArtifactsTreeNode node = (ArtifactsTreeNode)getChildAt(i);
      if (node.getName().equals(name)) {
        return node;
      }
    }
    return null;
  }

  public abstract String getName();
}
