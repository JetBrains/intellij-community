// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.designer.componentTree;

import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.model.RadComponent;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ui.tree.TreeUtil;

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

  public ExpandStateHandler(ComponentTree tree, DesignerEditorPanel designer) {
    myTree = tree;
    myDesigner = designer;
  }

  public void hookListener() {
    myTree.addTreeExpansionListener(this);
  }

  public void unhookListener() {
    myTree.removeTreeExpansionListener(this);
  }

  public Object[] getExpanded() {
    List<?> components = myDesigner.getExpandedComponents();
    return components == null ? ArrayUtilRt.EMPTY_OBJECT_ARRAY : components.toArray();
  }

  private void setExpanded() {
    List<Object> elements = TreeUtil.collectExpandedUserObjects(myTree);
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