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

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.designer.DesignerBundle;
import com.intellij.designer.actions.DesignerActionPanel;
import com.intellij.designer.actions.SelectAllAction;
import com.intellij.designer.actions.StartInplaceEditing;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.designSurface.FeedbackTreeLayer;
import com.intellij.designer.model.ErrorInfo;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.plaf.TreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public final class ComponentTree extends Tree implements UiDataProvider {
  private final StartInplaceEditing myInplaceEditingAction;
  private QuickFixManager myQuickFixManager;
  private DesignerEditorPanel myDesigner;
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

  @Override
  public void setUI(TreeUI ui) {
    super.setUI(ui);
    getActionMap().put("selectAll", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myDesigner != null) {
          ((SelectAllAction)myDesigner.getActionPanel().createSelectAllAction(myDesigner.getSurfaceArea())).perform();
        }
      }
    });
  }

  public void newModel() {
    setModel(new DefaultTreeModel(new DefaultMutableTreeNode()));
  }

  public void initQuickFixManager(JViewport viewPort) {
    myQuickFixManager = new QuickFixManager(this, viewPort);
  }

  public void updateInspections() {
    myQuickFixManager.update();
  }

  public void setDesignerPanel(@Nullable DesignerEditorPanel designer) {
    myDesigner = designer;
    myMarkComponent = null;
    myArea = null;
    myInplaceEditingAction.setDesignerPanel(designer);
    myQuickFixManager.setDesigner(designer);
  }

  public void setArea(@Nullable EditableArea area) {
    myArea = area;
    myQuickFixManager.setEditableArea(area);
  }

  public void mark(RadComponent component, int feedback) {
    myMarkComponent = component;
    myMarkFeedback = feedback;
    repaint();
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(EditableArea.DATA_KEY, myArea);
    if (myDesigner != null) {
      sink.set(PlatformCoreDataKeys.FILE_EDITOR, myDesigner.getEditor());
      DataSink.uiDataSnapshot(sink, myDesigner.getActionPanel());
    }
  }

  @Nullable
  public RadComponent extractComponent(Object value) {
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
    Object userObject = node.getUserObject();

    if (myDesigner != null && userObject instanceof TreeNodeDescriptor descriptor) {
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

  @Override
  public String getToolTipText(MouseEvent event) {
    TreePath path = getPathForLocation(event.getX(), event.getY());
    if (path != null) {
      RadComponent component = extractComponent(path.getLastPathComponent());
      if (component != null) {
        List<ErrorInfo> errorInfos = RadComponent.getError(component);
        if (!errorInfos.isEmpty()) {
          return errorInfos.get(0).getName();
        }
      }
    }
    return super.getToolTipText(event);
  }

  @Nullable
  private static HighlightDisplayLevel getHighlightDisplayLevel(Project project, RadComponent component) {
    HighlightDisplayLevel displayLevel = null;
    SeverityRegistrar severityRegistrar = SeverityRegistrar.getSeverityRegistrar(project);
    for (ErrorInfo errorInfo : RadComponent.getError(component)) {
      if (displayLevel == null || severityRegistrar.compare(errorInfo.getLevel().getSeverity(), displayLevel.getSeverity()) > 0) {
        displayLevel = errorInfo.getLevel();
      }
    }
    return displayLevel;
  }

  private AttributeWrapper getAttributeWrapper(RadComponent component) {
    AttributeWrapper wrapper = AttributeWrapper.DEFAULT;
    final HighlightDisplayLevel level = getHighlightDisplayLevel(myDesigner.getProject(), component);

    if (level != null) {
      TextAttributesKey attributesKey =
        SeverityRegistrar.getSeverityRegistrar(myDesigner.getProject()).getHighlightInfoTypeBySeverity(level.getSeverity())
          .getAttributesKey();
      final TextAttributes textAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(attributesKey);

      wrapper = new AttributeWrapper() {
        @Override
        public SimpleTextAttributes getAttribute(SimpleTextAttributes attributes) {
          Color bgColor = textAttributes.getBackgroundColor();
          try {
            textAttributes.setBackgroundColor(null);
            return SimpleTextAttributes.fromTextAttributes(TextAttributes.merge(attributes.toTextAttributes(), textAttributes));
          }
          finally {
            textAttributes.setBackgroundColor(bgColor);
          }
        }
      };
    }

    return wrapper;
  }

  private void installCellRenderer() {
    setCellRenderer(new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        try {
          RadComponent component = extractComponent(value);

          if (component != null) {
            myDesigner.getTreeDecorator().decorate(component, this, getAttributeWrapper(component), true);

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
        catch (RuntimeException e) {
          if (myDesigner == null) {
            throw e;
          }
          myDesigner.showError(DesignerBundle.message("designer.error.tree.paint.operation"), e);
        }
      }
    });
  }

  private static class InsertBorder extends LineBorder {
    private final int myMode;

    InsertBorder(int mode) {
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