package com.intellij.openapi.roots.ui.configuration.artifacts.nodes;

import com.intellij.openapi.roots.ui.configuration.artifacts.ComplexElementSubstitutionParameters;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTree;
import com.intellij.packaging.elements.ComplexPackagingElement;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.awt.event.InputEvent;

/**
 * @author nik
 */
public class ComplexPackagingElementNode extends PackagingElementNode<ComplexPackagingElement<?>> {
  private final ComplexElementSubstitutionParameters mySubstitutionParameters;

  public ComplexPackagingElementNode(ComplexPackagingElement<?> element, PackagingEditorContext context, PackagingElementNode<?> parent,
                                     ComplexElementSubstitutionParameters substitutionParameters) {
    super(element, context, parent);
    mySubstitutionParameters = substitutionParameters;
  }

  @Override
  public void handleDoubleClickOrEnter(SimpleTree tree, InputEvent inputEvent) {
    mySubstitutionParameters.substitute(this);
    final LayoutTree layoutTree = (LayoutTree)tree;
    final DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(layoutTree.getRootNode(), this);
    if (node != null) {
      final TreeNode parent = node.getParent();
      if (parent instanceof DefaultMutableTreeNode) {
        layoutTree.addSubtreeToUpdate((DefaultMutableTreeNode)parent);
      }
    }
  }
}
