// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.ui.UIBundle;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Arrays;
import java.util.List;

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

  @NotNull List<AnAction> createActions() {
    AnAction expandAll =
      new AnAction(UIBundle.messagePointer("action.DesignerToolWindow.Anonymous.text.expand.all"), AllIcons.Actions.Expandall) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (myTreeBuilder != null) {
          TreeUtil.expandAll(myComponentTree);
        }
      }
    };

    AnAction collapseAll =
      new AnAction(UIBundle.messagePointer("action.DesignerToolWindow.Anonymous.text.collapse.all"), AllIcons.Actions.Collapseall) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (myTreeBuilder != null) {
          TreeUtil.collapseAll(myComponentTree, 1);
        }
      }
    };

    return Arrays.asList(expandAll, collapseAll);
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