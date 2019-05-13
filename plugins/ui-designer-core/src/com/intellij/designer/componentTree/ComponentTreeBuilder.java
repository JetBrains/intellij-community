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

import com.intellij.designer.designSurface.ComponentGlassLayer;
import com.intellij.designer.designSurface.ComponentSelectionListener;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;

import javax.swing.tree.DefaultTreeModel;

/**
 * @author Alexander Lobas
 */
public final class ComponentTreeBuilder extends AbstractTreeBuilder implements ComponentSelectionListener {
  private final EditableArea mySurfaceArea;
  private final TreeEditableArea myTreeArea;
  private final ComponentGlassLayer myGlassLayer;
  private final ExpandStateHandler myExpandStateHandler;

  public ComponentTreeBuilder(ComponentTree tree, DesignerEditorPanel designer) {
    super(tree, (DefaultTreeModel)tree.getModel(), new TreeContentProvider(designer), null);

    setPassthroughMode(true);
    setCanYieldUpdate(false);

    initRootNode();

    mySurfaceArea = designer.getSurfaceArea();
    myTreeArea = new TreeEditableArea(tree, this, designer.getActionPanel());
    myGlassLayer = new ComponentGlassLayer(tree, designer.getToolProvider(), myTreeArea);
    myExpandStateHandler = new ExpandStateHandler(tree, designer, this);

    tree.setArea(myTreeArea);
    designer.handleTreeArea(myTreeArea);

    new TreeDropListener(tree, myTreeArea, designer.getToolProvider());

    selectFromSurface();
    expandFromState();

    addListeners();
    myExpandStateHandler.hookListener();
  }

  public TreeEditableArea getTreeArea() {
    return myTreeArea;
  }

  @Override
  public void dispose() {
    removeListeners();
    myTreeArea.unhookSelection();
    myGlassLayer.dispose();
    myExpandStateHandler.unhookListener();
    super.dispose();
  }

  private void addListeners() {
    mySurfaceArea.addSelectionListener(this);
    myTreeArea.addSelectionListener(this);
  }

  private void removeListeners() {
    mySurfaceArea.removeSelectionListener(this);
    myTreeArea.removeSelectionListener(this);
  }

  @Override
  public void selectionChanged(EditableArea area) {
    try {
      removeListeners();
      if (mySurfaceArea == area) {
        try {
          myTreeArea.setCanvasSelection(true);
          myTreeArea.setSelection(mySurfaceArea.getSelection());
        }
        finally {
          myTreeArea.setCanvasSelection(false);
        }
      }
      else {
        mySurfaceArea.setSelection(myTreeArea.getSelection());
        mySurfaceArea.scrollToSelection();
      }
    }
    finally {
      addListeners();
    }
  }

  public void selectFromSurface() {
    myTreeArea.setSelection(mySurfaceArea.getSelection());
  }

  public void expandFromState() {
    expand(myExpandStateHandler.getExpanded(), null);
  }
}