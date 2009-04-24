package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.elements.CompositePackagingElement;

/**
 * @author nik
 */
public class PackagingElementDraggingObject {
  private final PackagingSourceItem[] mySourceItems;
  private CompositePackagingElement<?> myTarget;

  public PackagingElementDraggingObject(PackagingSourceItem[] sourceItems) {
    mySourceItems = sourceItems;
  }

  public PackagingSourceItem[] getSourceItems() {
    return mySourceItems;
  }

  public void setTarget(CompositePackagingElement<?> target) {
    myTarget = target;
  }

  public CompositePackagingElement<?> getTarget() {
    return myTarget;
  }
}
