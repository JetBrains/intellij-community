package com.intellij.openapi.roots.ui.configuration.artifacts.nodes;

import com.intellij.openapi.roots.ui.configuration.artifacts.ComplexElementSubstitutionParameters;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.ui.treeStructure.SimpleNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class CompositePackagingElementNode extends PackagingElementNode<CompositePackagingElement<?>> {
  private final ComplexElementSubstitutionParameters mySubstitutionParameters;
  private final ArtifactType myArtifactType;

  public CompositePackagingElementNode(CompositePackagingElement<?> packagingElement, ArtifactEditorContext context,
                                       CompositePackagingElementNode parentNode, CompositePackagingElement<?> parentElement,
                                       ComplexElementSubstitutionParameters substitutionParameters,
                                       Collection<PackagingNodeSource> nodeSources, ArtifactType artifactType) {
    super(packagingElement, context, parentNode, parentElement, nodeSources);
    mySubstitutionParameters = substitutionParameters;
    myArtifactType = artifactType;
  }

  @Override
  protected SimpleNode[] buildChildren() {
    List<PackagingElementNode<?>> children = new ArrayList<PackagingElementNode<?>>();
    for (CompositePackagingElement<?> element : getPackagingElements()) {
      PackagingTreeNodeFactory.addNodes(element.getChildren(), this, element, myContext, mySubstitutionParameters, getNodeSource(element), children,
                                        myArtifactType);
    }
    return children.toArray(new SimpleNode[children.size()]);
  }

}
