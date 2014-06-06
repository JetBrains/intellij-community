/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.designer;

import com.intellij.designer.componentTree.ComponentTree;
import com.intellij.designer.componentTree.ComponentTreeBuilder;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.propertyTable.PropertyTablePanel;
import com.intellij.designer.propertyTable.RadPropertyTable;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * @author Alexander Lobas
 */
public final class DesignerToolWindow implements DesignerToolWindowContent {
  private final Splitter myToolWindowPanel;
  private ComponentTree myComponentTree;
  private ComponentTreeBuilder myTreeBuilder;
  private PropertyTablePanel myPropertyTablePanel;

  public DesignerToolWindow(Project project, boolean updateOrientation) {
    myComponentTree = new ComponentTree();
    JScrollPane treeScrollPane = ScrollPaneFactory.createScrollPane(myComponentTree);
    treeScrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    treeScrollPane.setPreferredSize(new Dimension(250, -1));
    myComponentTree.initQuickFixManager(treeScrollPane.getViewport());

    myPropertyTablePanel = new PropertyTablePanel(project);

    myToolWindowPanel = new Splitter(true, 0.42f) {
      @Override
      public void doLayout() {
        super.doLayout();

        JComponent firstComponent = getFirstComponent();
        JComponent secondComponent = getSecondComponent();
        if (firstComponent == null || secondComponent == null) {
          return;
        }

        int firstHeight = firstComponent.getHeight();
        int dividerHeight = getDivider().getHeight();
        int height = getSize().height;

        if (firstHeight + dividerHeight + secondComponent.getHeight() != height) {
          Rectangle bounds = secondComponent.getBounds();
          bounds.height = height - firstHeight - dividerHeight;
          secondComponent.setBounds(bounds);
        }
      }
    };

    myToolWindowPanel.setDividerWidth(3);
    myToolWindowPanel.setShowDividerControls(false);
    myToolWindowPanel.setShowDividerIcon(false);
    myToolWindowPanel.setFirstComponent(treeScrollPane);
    myToolWindowPanel.setSecondComponent(myPropertyTablePanel);

    if (updateOrientation) {
      myToolWindowPanel.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          Dimension size = myToolWindowPanel.getSize();
          boolean newVertical = size.width < size.height;
          if (myToolWindowPanel.getOrientation() != newVertical) {
            myToolWindowPanel.setOrientation(newVertical);
          }
        }
      });
    }
  }

  void update(DesignerEditorPanel designer) {
    clearTreeBuilder();
    myComponentTree.newModel();
    if (designer == null) {
      myComponentTree.setDesignerPanel(null);
      myPropertyTablePanel.setArea(null, null);
    }
    else {
      myComponentTree.setDesignerPanel(designer);
      myTreeBuilder = new ComponentTreeBuilder(myComponentTree, designer);
      myPropertyTablePanel.setArea(designer, myTreeBuilder.getTreeArea());
    }
  }

  @Override
  public void dispose() {
    clearTreeBuilder();
    myToolWindowPanel.dispose();
    myComponentTree = null;
    myPropertyTablePanel = null;
  }

  private void clearTreeBuilder() {
    if (myTreeBuilder != null) {
      Disposer.dispose(myTreeBuilder);
      myTreeBuilder = null;
    }
  }

  Splitter getToolWindowPanel() {
    return myToolWindowPanel;
  }

  AnAction[] createActions() {
    AnAction expandAll = new AnAction("Expand All", null, AllIcons.Actions.Expandall) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (myTreeBuilder != null) {
          TreeUtil.expandAll(myComponentTree);
        }
      }
    };

    AnAction collapseAll = new AnAction("Collapse All", null, AllIcons.Actions.Collapseall) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (myTreeBuilder != null) {
          TreeUtil.collapseAll(myComponentTree, 1);
        }
      }
    };

    return new AnAction[]{expandAll, collapseAll};
  }

  public ComponentTree getComponentTree() {
    return myComponentTree;
  }

  public RadPropertyTable getPropertyTable() {
    return myPropertyTablePanel.getPropertyTable();
  }

  @Override
  public void expandFromState() {
    if (myTreeBuilder != null) {
      myTreeBuilder.expandFromState();
    }
  }

  @Override
  public void refresh(boolean updateProperties) {
    if (myTreeBuilder != null) {
      if (updateProperties) {
        myTreeBuilder.selectFromSurface();
      }
      else {
        myTreeBuilder.queueUpdate();
      }
    }
  }

  @Override
  public void updateInspections() {
    if (myComponentTree != null) {
      myComponentTree.updateInspections();
    }
    if (myPropertyTablePanel != null) {
      myPropertyTablePanel.getPropertyTable().updateInspections();
    }
  }
}