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
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.util.ArrayUtil;

import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import java.util.Iterator;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class ExpandStateHandler implements TreeExpansionListener {
  private final ComponentTree myTree;
  private final DesignerEditorPanel myDesigner;
  private final AbstractTreeBuilder myTreeBuilder;

  public ExpandStateHandler(ComponentTree tree, DesignerEditorPanel designer, AbstractTreeBuilder treeBuilder) {
    myTree = tree;
    myDesigner = designer;
    myTreeBuilder = treeBuilder;
  }

  public void hookListener() {
    myTree.addTreeExpansionListener(this);
  }

  public void unhookListener() {
    myTree.removeTreeExpansionListener(this);
  }

  public Object[] getExpanded() {
    List<?> components = myDesigner.getExpandedComponents();
    return components == null ? ArrayUtil.EMPTY_OBJECT_ARRAY : components.toArray();
  }

  private void setExpanded() {
    List<Object> elements = myTreeBuilder.getUi().getExpandedElements();
    // remove root Object
    for (Iterator<Object> I = elements.iterator(); I.hasNext(); ) {
      Object element = I.next();
      if (!(element instanceof RadComponent)) {
        I.remove();
      }
    }
    myDesigner.setExpandedComponents(elements);
  }

  @Override
  public void treeExpanded(TreeExpansionEvent event) {
    setExpanded();
  }

  @Override
  public void treeCollapsed(TreeExpansionEvent event) {
    setExpanded();
  }
}