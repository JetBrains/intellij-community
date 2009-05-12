package com.intellij.openapi.roots.ui.configuration.artifacts.nodes;

import com.intellij.openapi.roots.ui.configuration.artifacts.ComplexElementSubstitutionParameters;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.ui.treeStructure.SimpleNode;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class CompositePackagingElementNode extends PackagingElementNode<CompositePackagingElement<?>> {
  private final ComplexElementSubstitutionParameters mySubstitutionParameters;

  public CompositePackagingElementNode(CompositePackagingElement<?> packagingElement, PackagingEditorContext context,
                                       PackagingElementNode<?> parent, ComplexElementSubstitutionParameters substitutionParameters) {
    super(packagingElement, context, parent);
    mySubstitutionParameters = substitutionParameters;
  }

  @Override
  public SimpleNode[] getChildren() {
    List<PackagingElement<?>> allElements = new ArrayList<PackagingElement<?>>();
    for (CompositePackagingElement<?> element : getPackagingElements()) {
      allElements.addAll(element.getChildren());
    }
    List<? extends PackagingElementNode<?>> children = PackagingTreeNodeFactory.createNodes(allElements, this, myContext, mySubstitutionParameters);
    return children.toArray(new SimpleNode[children.size()]);
  }

}
