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

import com.intellij.designer.designSurface.ComponentSelectionListener;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;

/**
 * @author Alexander Lobas
 */
public final class ComponentTreeBuilder extends AbstractTreeBuilder implements ComponentSelectionListener, TreeSelectionListener {
  private final TreeSelectionModel myTreeSelectionModel;
  private final EditableArea mySurfaceArea;
  private final TreeEditableArea myTreeArea;
  private final TreeGlassLayer myGlassLayer;

  public ComponentTreeBuilder(ComponentTree tree, DesignerEditorPanel designer) {
    super(tree, (DefaultTreeModel)tree.getModel(), new TreeContentProvider(designer), null);

    initRootNode();

    myTreeSelectionModel = getTree().getSelectionModel();
    mySurfaceArea = designer.getSurfaceArea();
    myTreeArea = new TreeEditableArea(tree, this);
    myGlassLayer = new TreeGlassLayer(tree, designer.getToolProvider(), myTreeArea);

    // TODO: restore expanded state
    select(mySurfaceArea.getSelection().toArray(), null);

    addListeners();
  }

  public TreeEditableArea getTreeArea() {
    return myTreeArea;
  }

  @Override
  public void dispose() {
    removeListeners();
    myGlassLayer.dispose();
    super.dispose();
  }

  private void addListeners() {
    mySurfaceArea.addSelectionListener(this);
    myTreeSelectionModel.addTreeSelectionListener(this);
  }

  private void removeListeners() {
    mySurfaceArea.removeSelectionListener(this);
    myTreeSelectionModel.removeTreeSelectionListener(this);
  }

  private void handleSelection(Runnable runnable) {
    try {
      removeListeners();
      runnable.run();
    }
    finally {
      addListeners();
    }
  }

  @Override
  public void selectionChanged(EditableArea area) {
    handleSelection(new Runnable() {
      @Override
      public void run() {
        myTreeArea.setSelection(mySurfaceArea.getSelection());
      }
    });
  }

  @Override
  public void valueChanged(TreeSelectionEvent e) {
    handleSelection(new Runnable() {
      @Override
      public void run() {
        mySurfaceArea.setSelection(myTreeArea.getSelection());
      }
    });
  }
}