// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer.componentTree;

import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.model.RadComponent;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Lobas
 */
public final class TreeContentProvider extends AbstractTreeStructure {
  private final DesignerEditorPanel myDesigner;
  private final Object myTreeRoot = new Object();

  public TreeContentProvider(DesignerEditorPanel designer) {
    myDesigner = designer;
  }

  @Override
  public @NotNull Object getRootElement() {
    return myTreeRoot;
  }

  @Override
  public Object @NotNull [] getChildElements(@NotNull Object element) {
    if (element == myTreeRoot) {
      return myDesigner.getTreeRoots();
    }
    if (element instanceof RadComponent component) {
      return component.getTreeChildren();
    }
    throw new IllegalArgumentException("Unknown element: " + element);
  }

  @Override
  public Object getParentElement(@NotNull Object element) {
    if (element instanceof RadComponent component) {
      return component.getParent();
    }
    return null;
  }

  @Override
  public @NotNull NodeDescriptor createDescriptor(@NotNull Object element, NodeDescriptor parentDescriptor) {
    if (element == myTreeRoot || element instanceof RadComponent) {
      TreeNodeDescriptor descriptor = new TreeNodeDescriptor(parentDescriptor, element);
      descriptor.setWasDeclaredAlwaysLeaf(isAlwaysLeaf(element));
      return descriptor;
    }
    throw new IllegalArgumentException("Unknown element: " + element);
  }

  @Override
  public boolean isAlwaysLeaf(@NotNull Object element) {
    return element instanceof RadComponent && ((RadComponent)element).getTreeChildren().length == 0;
  }

  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }

  @Override
  public void commit() {
  }
}