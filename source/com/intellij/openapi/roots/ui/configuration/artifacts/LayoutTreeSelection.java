package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class LayoutTreeSelection {
  private List<PackagingElementNode<?>> mySelectedNodes = new ArrayList<PackagingElementNode<?>>();
  private List<PackagingElement<?>> mySelectedElements = new ArrayList<PackagingElement<?>>();
  private Map<PackagingElement<?>, PackagingElementNode<?>> myElement2Node = new HashMap<PackagingElement<?>, PackagingElementNode<?>>();
  private Map<PackagingElementNode<?>, TreePath> myNode2Path = new HashMap<PackagingElementNode<?>, TreePath>();
  private final LayoutTree myLayoutTree;

  public LayoutTreeSelection(@NotNull LayoutTree tree) {
    myLayoutTree = tree;
    final TreePath[] paths = tree.getSelectionPaths();
    if (paths == null) {
      return;
    }

    for (TreePath path : paths) {
      final SimpleNode node = tree.getNodeFor(path);
      if (node instanceof PackagingElementNode) {
        final PackagingElementNode<?> elementNode = (PackagingElementNode<?>)node;
        mySelectedNodes.add(elementNode);
        myNode2Path.put(elementNode, path);
        for (PackagingElement<?> element : elementNode.getPackagingElements()) {
          mySelectedElements.add(element);
          myElement2Node.put(element, elementNode);
        }
      }
    }
  }

  public List<PackagingElementNode<?>> getSelectedNodes() {
    return mySelectedNodes;
  }

  public List<PackagingElement<?>> getSelectedElements() {
    return mySelectedElements;
  }

  public PackagingElementNode<?> getNode(@NotNull PackagingElement<?> element) {
    return myElement2Node.get(element);
  }

  public TreePath getPath(@NotNull PackagingElementNode<?> node) {
    return myNode2Path.get(node);
  }

  @Nullable
  public PackagingElementNode<?> getParentNode(@NotNull PackagingElementNode<?> selectedNode) {
    final TreePath path = myNode2Path.get(selectedNode);
    final TreePath parentPath = path.getParentPath();
    if (parentPath == null) return null;
    final SimpleNode parentNode = myLayoutTree.getNodeFor(parentPath);
    return parentNode instanceof PackagingElementNode ? (PackagingElementNode<?>)parentNode : null;
  }

  @Nullable
  public CompositePackagingElement<?> getCommonParentElement() {
    PackagingElementNode<?> parentNode = null;
    for (PackagingElementNode<?> selectedNode : mySelectedNodes) {
      final PackagingElementNode<?> node = getParentNode(selectedNode);
      if (node == null || parentNode != null && parentNode != node) {
        return null;
      }
      parentNode = node;
    }
    if (parentNode == null) {
      return null;
    }
    final PackagingElement<?> element = parentNode.getElementIfSingle();
    return element instanceof CompositePackagingElement ? (CompositePackagingElement<?>)element : null;
  }

  @Nullable
  public PackagingElement<?> getElementIfSingle() {
    return mySelectedElements.size() == 1 ? mySelectedElements.get(0) : null;
  }

  @Nullable
  public PackagingElementNode<?> getNodeIfSingle() {
    return mySelectedNodes.size() == 1 ? mySelectedNodes.get(0) : null;
  }
}
