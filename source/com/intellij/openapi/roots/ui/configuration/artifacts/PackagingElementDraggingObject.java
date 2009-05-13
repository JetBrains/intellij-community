package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;

import java.util.List;

/**
 * @author nik
 */
public abstract class PackagingElementDraggingObject {
  private PackagingElementNode<?> myTargetNode;
  private CompositePackagingElement<?> myTargetElement;

  public abstract List<PackagingElement<?>> createPackagingElements();

  public void setTargetNode(PackagingElementNode<?> targetNode) {
    myTargetNode = targetNode;
  }

  public PackagingElementNode<?> getTargetNode() {
    return myTargetNode;
  }

  public CompositePackagingElement<?> getTargetElement() {
    return myTargetElement;
  }

  public void setTargetElement(CompositePackagingElement<?> targetElement) {
    myTargetElement = targetElement;
  }
}
