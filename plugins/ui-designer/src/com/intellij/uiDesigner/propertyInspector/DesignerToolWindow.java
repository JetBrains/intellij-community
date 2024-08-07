// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector;

import com.intellij.designer.LightToolWindowContent;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.uiDesigner.componentTree.ComponentTree;
import com.intellij.uiDesigner.componentTree.ComponentTreeBuilder;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class DesignerToolWindow implements LightToolWindowContent {
  private final MyToolWindowPanel myToolWindowPanel = new MyToolWindowPanel();
  private ComponentTree myComponentTree;
  private ComponentTreeBuilder myComponentTreeBuilder;
  private PropertyInspector myPropertyInspector;

  public DesignerToolWindow(@NotNull Project project) {
    myComponentTree = new ComponentTree(project);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myComponentTree);
    scrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    scrollPane.setPreferredSize(new Dimension(250, -1));
    myComponentTree.initQuickFixManager(scrollPane.getViewport());

    myPropertyInspector = new PropertyInspector(project, myComponentTree);

    myToolWindowPanel.setFirstComponent(scrollPane);
    myToolWindowPanel.setSecondComponent(myPropertyInspector);
  }

  @Override
  public void dispose() {
    clearTreeBuilder();
    myToolWindowPanel.dispose();
    myComponentTree = null;
    myPropertyInspector = null;
  }

  private void clearTreeBuilder() {
    if (myComponentTreeBuilder != null) {
      Disposer.dispose(myComponentTreeBuilder);
      myComponentTreeBuilder = null;
    }
  }

  public void update(GuiEditor designer) {
    clearTreeBuilder();

    myComponentTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode()));
    myComponentTree.setEditor(designer);
    myPropertyInspector.setEditor(designer);

    if (designer == null) {
      myComponentTree.setFormEditor(null);
    }
    else {
      myComponentTree.setFormEditor(designer.getEditor());
      myComponentTreeBuilder = new ComponentTreeBuilder(myComponentTree, designer);
    }
  }

  public JComponent getToolWindowPanel() {
    return myToolWindowPanel;
  }

  public ComponentTree getComponentTree() {
    return myComponentTree;
  }

  public ComponentTreeBuilder getComponentTreeBuilder() {
    return myComponentTreeBuilder;
  }

  public void updateComponentTree() {
    if (myComponentTreeBuilder != null) {
      myComponentTreeBuilder.invalidateAsync();
    }
  }

  public PropertyInspector getPropertyInspector() {
    return myPropertyInspector;
  }

  public void refreshErrors() {
    if (myComponentTree != null) {
      myComponentTree.refreshIntentionHint();
      myComponentTree.repaint(myComponentTree.getVisibleRect());
    }

    // PropertyInspector
    if (myPropertyInspector != null) {
      myPropertyInspector.refreshIntentionHint();
      myPropertyInspector.repaint(myPropertyInspector.getVisibleRect());
    }
  }

  private class MyToolWindowPanel extends Splitter implements UiDataProvider {
    MyToolWindowPanel() {
      super(true, 0.33f);
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      if (myComponentTree == null) return;
      sink.set(GuiEditor.DATA_KEY, myComponentTree.getEditor());
    }
  }
}