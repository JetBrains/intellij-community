/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  public Object getRootElement() {
    return myTreeRoot;
  }

  @Override
  public Object[] getChildElements(Object element) {
    if (element == myTreeRoot) {
      return myDesigner.getTreeRoots();
    }
    if (element instanceof RadComponent) {
      RadComponent component = (RadComponent)element;
      return component.getTreeChildren();
    }
    throw new IllegalArgumentException("Unknown element: " + element);
  }

  @Override
  public Object getParentElement(Object element) {
    if (element instanceof RadComponent) {
      RadComponent component = (RadComponent)element;
      return component.getParent();
    }
    return null;
  }

  @NotNull
  @Override
  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    if (element == myTreeRoot || element instanceof RadComponent) {
      TreeNodeDescriptor descriptor = new TreeNodeDescriptor(parentDescriptor, element);
      descriptor.setWasDeclaredAlwaysLeaf(isAlwaysLeaf(element));
      return descriptor;
    }
    throw new IllegalArgumentException("Unknown element: " + element);
  }

  @Override
  public boolean isAlwaysLeaf(Object element) {
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