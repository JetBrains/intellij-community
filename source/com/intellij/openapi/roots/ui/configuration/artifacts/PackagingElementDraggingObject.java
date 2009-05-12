package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.elements.CompositePackagingElement;

/**
 * @author nik
 */
public class PackagingElementDraggingObject {
  private final PackagingSourceItem[] mySourceItems;
  private PackagingElementNode<?> myTargetNode;
  private CompositePackagingElement<?> myTargetElement;

  public PackagingElementDraggingObject(PackagingSourceItem[] sourceItems) {
    mySourceItems = sourceItems;
  }

  public PackagingSourceItem[] getSourceItems() {
    return mySourceItems;
  }

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
