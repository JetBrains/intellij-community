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
package com.intellij.designer;

import com.intellij.designer.componentTree.ComponentTree;
import com.intellij.designer.componentTree.ComponentTreeBuilder;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.propertyTable.PropertyTablePanel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.tree.TreeUtil;
import icons.UIDesignerNewIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * @author Alexander Lobas
 */
public final class DesignerToolWindowManager extends AbstractToolWindowManager {
  private Splitter myToolWindowPanel;
  private ComponentTree myComponentTree;
  private ComponentTreeBuilder myTreeBuilder;
  private PropertyTablePanel myPropertyTablePanel;

  public DesignerToolWindowManager(Project project, FileEditorManager fileEditorManager) {
    super(project, fileEditorManager);
  }


  @Override
  public void disposeComponent() {
    clearTreeBuilder();
    myComponentTree = null;
    myPropertyTablePanel = null;
  }

  private void clearTreeBuilder() {
    if (myTreeBuilder != null) {
      Disposer.dispose(myTreeBuilder);
      myTreeBuilder = null;
    }
  }

  public static DesignerToolWindowManager getInstance(Project project) {
    return project.getComponent(DesignerToolWindowManager.class);
  }

  public void expandFromState() {
    if (myTreeBuilder != null) {
      myTreeBuilder.expandFromState();
    }
  }

  public void refresh(boolean updateProperties) {
    if (myTreeBuilder != null) {
      if (updateProperties) {
        myTreeBuilder.selectFromSurface();
      }
      else {
        myComponentTree.repaint();
      }
    }
  }

  public void updateInspections() {
    if (myComponentTree != null) {
      myComponentTree.updateInspections();
    }
    if (myPropertyTablePanel != null) {
      myPropertyTablePanel.getPropertyTable().updateInspections();
    }
  }

  @Override
  protected void updateToolWindow(@Nullable DesignerEditorPanel designer) {
    clearTreeBuilder();
    myComponentTree.newModel();
    if (designer == null) {
      myComponentTree.setDesignerPanel(null);
      myPropertyTablePanel.getPropertyTable().setArea(null, null);
      myToolWindow.setAvailable(false, null);
    }
    else {
      myComponentTree.setDesignerPanel(designer);
      myTreeBuilder = new ComponentTreeBuilder(myComponentTree, designer);
      myPropertyTablePanel.getPropertyTable().setArea(designer, myTreeBuilder.getTreeArea());
      myToolWindow.setAvailable(true, null);
      myToolWindow.show(null);
    }
  }

  @Override
  protected void initToolWindow() {
    myComponentTree = new ComponentTree();
    JScrollPane treeScrollPane = ScrollPaneFactory.createScrollPane(myComponentTree);
    treeScrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    treeScrollPane.setPreferredSize(new Dimension(250, -1));
    myComponentTree.initQuickFixManager(treeScrollPane.getViewport());

    myPropertyTablePanel = new PropertyTablePanel(myProject);

    myToolWindowPanel = new Splitter(true, 0.42f);
    myToolWindowPanel.setFirstComponent(treeScrollPane);
    myToolWindowPanel.setSecondComponent(myPropertyTablePanel);
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

    myToolWindow =
      ToolWindowManager.getInstance(myProject)
        .registerToolWindow(DesignerBundle.message("designer.toolwindow.name"), false, ToolWindowAnchor.LEFT, myProject, true);
    myToolWindow.setIcon(UIDesignerNewIcons.ToolWindow);

    ((ToolWindowEx)myToolWindow).setTitleActions(createActions());

    ContentManager contentManager = myToolWindow.getContentManager();
    Content content =
      contentManager.getFactory().createContent(myToolWindowPanel, DesignerBundle.message("designer.toolwindow.title"), false);
    content.setCloseable(false);
    content.setPreferredFocusableComponent(myComponentTree);
    contentManager.addContent(content);
    contentManager.setSelectedContent(content, true);
    myToolWindow.setAvailable(false, null);
  }

  private AnAction[] createActions() {
    AnAction expandAll = new AnAction("Expand All", null, AllIcons.Actions.Expandall) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (myTreeBuilder != null) {
          myTreeBuilder.expandAll(null);
        }
      }
    };

    AnAction collapseAll = new AnAction("Collapse All", null, AllIcons.General.CollapseAll) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (myTreeBuilder != null) {
          TreeUtil.collapseAll(myComponentTree, 1);
        }
      }
    };

    return new AnAction[]{expandAll, collapseAll};
  }

  @NotNull
  @NonNls
  @Override
  public String getComponentName() {
    return "UIDesignerToolWindowManager2";
  }
}
