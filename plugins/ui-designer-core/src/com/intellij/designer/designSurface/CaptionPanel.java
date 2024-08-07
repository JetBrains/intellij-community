// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.designer.designSurface;

import com.intellij.designer.actions.CommonEditActionsProvider;
import com.intellij.designer.designSurface.tools.InputTool;
import com.intellij.designer.model.FindComponentVisitor;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadVisualComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLayeredPane;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class CaptionPanel extends JBLayeredPane implements UiDataProvider {
  private static final int SIZE = 16;

  private final boolean myHorizontal;
  private final EditableArea myMainArea;
  private final EditableArea myArea;
  private final DecorationLayer myDecorationLayer;
  private final FeedbackLayer myFeedbackLayer;
  private DefaultActionGroup myActionGroup;
  private final CommonEditActionsProvider myActionsProvider;
  private final RadVisualComponent myRootComponent;
  private List<RadComponent> myRootChildren = Collections.emptyList();
  private ICaption myCaption;

  public CaptionPanel(DesignerEditorPanel designer, boolean horizontal, boolean addBorder) {
    if (addBorder) {
      setBorder(IdeBorderFactory.createBorder(horizontal ? SideBorder.BOTTOM : SideBorder.RIGHT));
    }

    setFullOverlayLayout(true);
    setFocusable(true);

    myHorizontal = horizontal;
    myMainArea = designer.getSurfaceArea();

    myRootComponent = new RadVisualComponent() {
      @Override
      public List<RadComponent> getChildren() {
        return myRootChildren;
      }

      @Override
      public boolean canDelete() {
        return false;
      }
    };
    myRootComponent.setNativeComponent(this);
    if (horizontal) {
      myRootComponent.setBounds(0, 0, 100000, SIZE);
    }
    else {
      myRootComponent.setBounds(0, 0, SIZE, 100000);
    }

    myArea = new ComponentEditableArea(this) {
      @Override
      protected void fireSelectionChanged() {
        super.fireSelectionChanged();
        revalidate();
        repaint();
      }

      @Override
      public RadComponent findTarget(int x, int y, @Nullable ComponentTargetFilter filter) {
        FindComponentVisitor visitor = new FindComponentVisitor(CaptionPanel.this, filter, x, y);
        myRootComponent.accept(visitor, false);
        return visitor.getResult();
      }

      @Override
      public InputTool findTargetTool(int x, int y) {
        return myDecorationLayer.findTargetTool(x, y);
      }

      @Override
      public void showSelection(boolean value) {
        myDecorationLayer.showSelection(value);
      }

      @Override
      public ComponentDecorator getRootSelectionDecorator() {
        return EmptyComponentDecorator.INSTANCE;
      }

      @Override
      public EditOperation processRootOperation(OperationContext context) {
        return null;
      }

      @Override
      public FeedbackLayer getFeedbackLayer() {
        return myFeedbackLayer;
      }

      @Override
      public RadComponent getRootComponent() {
        return myRootComponent;
      }

      @Override
      public ActionGroup getPopupActions() {
        if (myActionGroup == null) {
          myActionGroup = new DefaultActionGroup();
          myActionGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_DELETE));
        }
        return myActionGroup;
      }

      @Override
      public String getPopupPlace() {
        return "UIDesigner.CaptionPanel";
      }
    };

    add(new GlassLayer(designer.getToolProvider(), myArea), DesignerEditorPanel.LAYER_GLASS);

    myDecorationLayer = new DecorationLayer(designer, myArea);
    add(myDecorationLayer, DesignerEditorPanel.LAYER_DECORATION);

    myFeedbackLayer = new FeedbackLayer(designer);
    add(myFeedbackLayer, DesignerEditorPanel.LAYER_FEEDBACK);

    myActionsProvider = new CommonEditActionsProvider(designer) {
      @Override
      protected EditableArea getArea(DataContext dataContext) {
        return myArea;
      }
    };

    myMainArea.addSelectionListener(new ComponentSelectionListener() {
      @Override
      public void selectionChanged(EditableArea area) {
        update();
      }
    });
  }

  public void attachToScrollPane(JScrollPane scrollPane) {
    scrollPane.getViewport().addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        repaint();
      }
    });
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(SIZE, SIZE);
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, myActionsProvider);
  }

  public void update() {
    List<RadComponent> selection = myMainArea.getSelection();
    if (selection.size() != 1) {
      if (myCaption != null) {
        myCaption = null;
        myRootComponent.setLayout(null);
        myRootChildren = Collections.emptyList();
        myArea.deselectAll();
        revalidate();
        repaint();
      }
      return;
    }

    boolean update = !myRootChildren.isEmpty();

    IntList oldSelection = null;
    if (myCaption != null) {
      oldSelection = new IntArrayList();
      for (RadComponent component : myArea.getSelection()) {
        oldSelection.add(myRootChildren.indexOf(component));
      }
    }

    myArea.deselectAll();
    myRootComponent.setLayout(null);

    ICaption caption = null;
    RadComponent component = selection.get(0);
    RadComponent parent = component.getParent();

    if (parent != null) {
      caption = parent.getLayout().getCaption(component);
    }
    if (caption == null) {
      caption = component.getCaption();
    }

    if (caption == null) {
      myRootChildren = Collections.emptyList();
    }
    else {
      myRootComponent.setLayout(caption.getCaptionLayout(myMainArea, myHorizontal));

      myRootChildren = caption.getCaptionChildren(myMainArea, myHorizontal);
      for (RadComponent child : myRootChildren) {
        child.setParent(myRootComponent);
      }

      if (myCaption == caption) {
        List<RadComponent> newSelection = new ArrayList<>();
        int componentSize = myRootChildren.size();
        int selectionSize = oldSelection.size();

        for (int i = 0; i < selectionSize; i++) {
          int index = oldSelection.getInt(i);
          if (0 <= index && index < componentSize) {
            newSelection.add(myRootChildren.get(index));
          }
        }

        if (!newSelection.isEmpty()) {
          myArea.setSelection(newSelection);
        }
      }

      update |= !myRootChildren.isEmpty();
    }

    myCaption = caption;

    if (update) {
      revalidate();
      repaint();
    }
  }
}