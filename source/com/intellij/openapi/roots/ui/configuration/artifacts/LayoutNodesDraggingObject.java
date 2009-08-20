package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class LayoutNodesDraggingObject extends PackagingElementDraggingObject {
  private final ArtifactEditorEx myArtifactsEditor;
  private final List<PackagingElementNode<?>> myNodes;

  public LayoutNodesDraggingObject(ArtifactEditorEx artifactsEditor, List<PackagingElementNode<?>> nodes) {
    myArtifactsEditor = artifactsEditor;
    myNodes = nodes;
  }

  public List<PackagingElement<?>> createPackagingElements(ArtifactEditorContext context) {
    final List<PackagingElement<?>> result = new ArrayList<PackagingElement<?>>();

    for (PackagingElementNode<?> node : myNodes) {
      final List<? extends PackagingElement<?>> elements = node.getPackagingElements();
      for (PackagingElement<?> element : elements) {
        result.add(ArtifactUtil.copyWithChildren(element, myArtifactsEditor.getContext().getProject()));
      }
    }

    return result;
  }

  @Override
  public boolean checkCanDrop() {
    return myArtifactsEditor.getLayoutTreeComponent().checkCanRemove(myNodes);
  }

  @Override
  public void beforeDrop() {
    myArtifactsEditor.getLayoutTreeComponent().removeNodes(myNodes);
  }

  @Override
  public boolean canDropInto(@NotNull PackagingElementNode node) {
    final LayoutTree tree = myArtifactsEditor.getLayoutTreeComponent().getLayoutTree();
    final TreePath path = tree.getPathFor(node);
    if (path != null) {
      for (PackagingElementNode<?> selectedNode : myNodes) {
        if (pathContains(path, selectedNode, tree)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean pathContains(TreePath path, PackagingElementNode<?> node, LayoutTree tree) {
    while (path != null) {
      final SimpleNode pathNode = tree.getNodeFor(path);
      if (pathNode == node) {
        return true;
      }
      path = path.getParentPath();
    }
    return false;
  }
}
