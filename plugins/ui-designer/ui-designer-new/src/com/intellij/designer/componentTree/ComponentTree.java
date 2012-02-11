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
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * @author Alexander Lobas
 */
public final class ComponentTree extends Tree implements DataProvider {
  private TreeComponentDecorator myDecorator;

  public ComponentTree() {
    newModel();

    installCellRenderer();

    setRootVisible(false);
    setShowsRootHandles(true);

    // Enable tooltips
    ToolTipManager.sharedInstance().registerComponent(this);

    // Install convenient keyboard navigation
    TreeUtil.installActions(this);

    // TODO: Popup menu

    // TODO: F2 should start inplace editing

    // TODO: DND
  }

  public void newModel() {
    setModel(new DefaultTreeModel(new DefaultMutableTreeNode()));
  }

  public void setDecorator(TreeComponentDecorator decorator) {
    myDecorator = decorator;
  }

  @Override
  public Object getData(@NonNls String dataId) {
    return null;  //TODO
  }

  private void installCellRenderer() {
    setCellRenderer(new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
        if (myDecorator != null && node.getUserObject() instanceof TreeNodeDescriptor) {
          TreeNodeDescriptor descriptor = (TreeNodeDescriptor)node.getUserObject();
          if (descriptor.getElement() instanceof RadComponent) {
            RadComponent component = (RadComponent)descriptor.getElement();
            // TODO: support more parameters and attributes
            myDecorator.decorate(component, this);
          }
        }
      }
    });
  }
}