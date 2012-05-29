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

import com.intellij.designer.actions.DesignerActionPanel;
import com.intellij.designer.actions.StartInplaceEditing;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.designSurface.FeedbackTreeLayer;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public final class ComponentTree extends Tree implements DataProvider {
  private final StartInplaceEditing myInplaceEditingAction;
  private TreeComponentDecorator myDecorator;
  private DesignerActionPanel myActionPanel;
  private EditableArea myArea;
  private RadComponent myMarkComponent;
  private int myMarkFeedback;

  public ComponentTree() {
    newModel();

    setScrollsOnExpand(true);
    installCellRenderer();

    setRootVisible(false);
    setShowsRootHandles(true);

    // Enable tooltips
    ToolTipManager.sharedInstance().registerComponent(this);

    // Install convenient keyboard navigation
    TreeUtil.installActions(this);

    myInplaceEditingAction = DesignerActionPanel.createInplaceEditingAction(this);
  }

  public void newModel() {
    setModel(new DefaultTreeModel(new DefaultMutableTreeNode()));
  }

  public void setDesignerPanel(@Nullable DesignerEditorPanel designer) {
    if (designer == null) {
      myDecorator = null;
      myActionPanel = null;
    }
    else {
      myDecorator = designer.getTreeDecorator();
      myActionPanel = designer.getActionPanel();
    }
    myMarkComponent = null;
    myArea = null;
    myInplaceEditingAction.setDesignerPanel(designer);
  }

  public void setArea(@Nullable EditableArea area) {
    myArea = area;
  }

  public void mark(RadComponent component, int feedback) {
    myMarkComponent = component;
    myMarkFeedback = feedback;
    repaint();
  }

  @Override
  public Object getData(@NonNls String dataId) {
    if (EditableArea.DATA_KEY.is(dataId)) {
      return myArea;
    }
    if (myActionPanel != null) {
      return myActionPanel.getData(dataId);
    }
    return null;
  }

  @Nullable
  public RadComponent extractComponent(Object value) {
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
    Object userObject = node.getUserObject();

    if (myDecorator != null && userObject instanceof TreeNodeDescriptor) {
      TreeNodeDescriptor descriptor = (TreeNodeDescriptor)userObject;
      Object element = descriptor.getElement();

      if (element instanceof RadComponent) {
        return (RadComponent)element;
      }
    }
    return null;
  }

  public int getEdgeSize() {
    return Math.max(5, ((JComponent)getCellRenderer()).getPreferredSize().height / 2 - 3);
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
        RadComponent component = extractComponent(value);
        if (component != null) {
          myDecorator.decorate(component, this, true);

          if (myMarkComponent == component) {
            if (myMarkFeedback == FeedbackTreeLayer.INSERT_SELECTION) {
              setBorder(BorderFactory.createLineBorder(Color.RED, 1));
            }
            else {
              setBorder(new InsertBorder(myMarkFeedback));
            }
          }
          else {
            setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
          }
        }
      }
    });
  }

  private static class InsertBorder extends LineBorder {
    private final int myMode;

    public InsertBorder(int mode) {
      super(Color.BLACK, 2);
      myMode = mode;
    }

    @Override
    public Insets getBorderInsets(Component component) {
      return getBorderInsets(component, new Insets(0, 0, 0, 0));
    }

    @Override
    public Insets getBorderInsets(Component component, Insets insets) {
      insets.top = myMode == FeedbackTreeLayer.INSERT_BEFORE ? thickness : 0;
      insets.left = insets.right = thickness;
      insets.bottom = myMode == FeedbackTreeLayer.INSERT_AFTER ? thickness : 0;
      return insets;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      Color oldColor = g.getColor();

      g.setColor(getLineColor());
      if (myMode == FeedbackTreeLayer.INSERT_BEFORE) {
        g.fillRect(x, y, width, thickness);
        g.fillRect(x, y, thickness, 2 * thickness);
        g.fillRect(x + width - thickness, y, thickness, 2 * thickness);
      }
      else {
        g.fillRect(x, y + height - thickness, width, thickness);
        g.fillRect(x, y + height - 2 * thickness, thickness, 2 * thickness);
        g.fillRect(x + width - thickness, y + height - 2 * thickness, thickness, 2 * thickness);
      }
      g.setColor(oldColor);
    }
  }
}