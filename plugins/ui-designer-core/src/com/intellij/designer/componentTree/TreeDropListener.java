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

import com.intellij.designer.clipboard.SimpleTransferable;
import com.intellij.designer.designSurface.ComponentTargetFilter;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.tools.CreationTool;
import com.intellij.designer.designSurface.tools.ToolProvider;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadComponentVisitor;
import com.intellij.designer.model.RadLayout;
import com.intellij.designer.palette.PaletteItem;
import com.intellij.designer.utils.Cursors;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class TreeDropListener extends DropTargetAdapter {
  private final EditableArea myArea;
  private final ToolProvider myToolProvider;
  private final Class[] myDragTargets;
  private final OperationContext myContext = new OperationContext();
  private DropTargetDragEvent myEvent;
  private EditOperation myTargetOperation;
  private RadComponent myTarget;
  private boolean myExecuteEnabled;
  private boolean myShowFeedback;

  public TreeDropListener(ComponentTree tree, EditableArea area, ToolProvider provider) {
    this(tree, area, provider, TreeDropListener.class, PaletteItem.class);
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      tree.setDragEnabled(true);
      tree.setTransferHandler(new TreeTransfer(TreeDropListener.class));
    }
  }

  public TreeDropListener(JComponent component, EditableArea area, ToolProvider provider, Class... dragTargets) {
    myArea = area;
    myContext.setArea(area);
    myToolProvider = provider;
    myDragTargets = dragTargets;
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      component.setDropTarget(new DropTarget(component, this));
    }
  }

  @Override
  public void dragExit(DropTargetEvent event) {
    eraseFeedback();
    clearState(false);
  }

  @Override
  public void dragOver(DropTargetDragEvent event) {
    myEvent = event;
    updateContext();
    updateTargetUnderMouse();
    updateCommand();
    showFeedback();
  }

  @Override
  public void drop(DropTargetDropEvent event) {
    event.acceptDrop(event.getDropAction());
    eraseFeedback();
    executeCommand();
    clearState(true);
    event.dropComplete(true);
  }

  private void showFeedback() {
    if (myTargetOperation != null) {
      myTargetOperation.showFeedback();
    }
    myShowFeedback = true;
  }

  private void eraseFeedback() {
    if (myShowFeedback) {
      myShowFeedback = false;
      if (myTargetOperation != null) {
        myTargetOperation.eraseFeedback();
      }
    }
  }

  private void updateContext() {
    myContext.setLocation(getLocation());

    if (myContext.getComponents() == null) {
      if (!ArrayUtil.contains(SimpleTransferable.getData(myEvent.getTransferable(), Class.class), myDragTargets)) {
        myContext.setComponents(Collections.<RadComponent>emptyList());
        return;
      }

      if (myToolProvider.getActiveTool() instanceof CreationTool) {
        myContext.setType(OperationContext.CREATE);
        CreationTool tool = (CreationTool)myToolProvider.getActiveTool();
        try {
          myContext.setComponents(Collections.singletonList(tool.getFactory().create()));
        }
        catch (Throwable e) {
          myContext.setComponents(Collections.<RadComponent>emptyList());
          myToolProvider.loadDefaultTool();
        }
        return;
      }

      List<RadComponent> components = RadComponent.getPureSelection(myArea.getSelection());

      RadComponent parent = null;
      for (RadComponent component : components) {
        if (parent == null) {
          parent = component.getParent();
        }
        else if (parent != component.getParent()) {
          components = Collections.emptyList();
          break;
        }
      }

      myContext.setComponents(components);

      myContext.resetMoveAddEnabled();
      for (RadComponent component : components) {
        component.processDropOperation(myContext);
      }
    }
  }

  private void updateTargetUnderMouse() {
    if (myContext.getComponents().isEmpty()) {
      return;
    }

    final List<RadComponent> excludeComponents = new ArrayList<>(myContext.getComponents());
    if (!myContext.isCreate()) {
      for (RadComponent component : myContext.getComponents()) {
        component.accept(new RadComponentVisitor() {
          @Override
          public void endVisit(RadComponent component) {
            excludeComponents.add(component);
          }
        }, true);
      }
    }

    final EditOperation[] operation = new EditOperation[1];
    ComponentTargetFilter filter = new ComponentTargetFilter() {
      @Override
      public boolean preFilter(RadComponent component) {
        return myContext.isCreate() || !excludeComponents.contains(component);
      }

      @Override
      public boolean resultFilter(RadComponent target) {
        if (!myContext.isCreate()) {
          if (myContext.getComponents().get(0).getParent() == target) {
            myContext.setType(OperationContext.MOVE);
          }
          else {
            myContext.setType(OperationContext.ADD);
          }
        }

        if (myTarget == target) {
          return true;
        }

        RadLayout layout = target.getLayout();
        if (layout != null) {
          operation[0] = layout.processChildOperation(myContext);
        }

        return operation[0] != null;
      }
    };
    Point location = getLocation();
    RadComponent target = myArea.findTarget(location.x, location.y, filter);

    if (target != myTarget) {
      if (myTargetOperation != null) {
        eraseFeedback();
      }

      myTarget = target;
      myTargetOperation = operation[0];
    }

    if (target == null) {
      if (!myContext.isCreate()) {
        myContext.setType(null);
      }
    }
    else {
      myTargetOperation.setComponents(myContext.getComponents());
    }
  }

  private Point getLocation() {
    return myEvent.getLocation();
  }

  private void updateCommand() {
    if (myTargetOperation != null) {
      if (myContext.isMove()) {
        setExecuteEnabled(myContext.isMoveEnabled() && myTargetOperation.canExecute());
      }
      else if (myContext.isAdd()) {
        setExecuteEnabled(myContext.isAddEnabled() && myTargetOperation.canExecute());
      }
      else if (myContext.isCreate()) {
        setExecuteEnabled(myTargetOperation.canExecute());
      }
      else {
        setExecuteEnabled(false);
      }
    }
    else {
      setExecuteEnabled(false);
    }
  }

  private static final Cursor myDragCursor = Cursors.getMoveCursor();

  private void setExecuteEnabled(boolean enabled) {
    myExecuteEnabled = enabled;
    if (enabled) {
      myEvent.acceptDrag(myEvent.getDropAction());
      myArea.setCursor(myContext.isCreate() ? Cursors.getCopyCursor() : myDragCursor);
    }
    else {
      myEvent.rejectDrag();
      myArea.setCursor(Cursors.getSystemNoCursor());
    }
  }

  private void executeCommand() {
    if (myExecuteEnabled) {
      myToolProvider.execute(Collections.singletonList(myTargetOperation), myContext.getMessage());
      if (myContext.isCreate()) {
        myArea.setSelection(myContext.getComponents());
      }
    }
  }

  private void clearState(boolean full) {
    myContext.setComponents(null);
    myEvent = null;
    myTargetOperation = null;
    myTarget = null;
    myExecuteEnabled = false;
    if (full || !myContext.isCreate()) {
      myContext.setType(null);
      myToolProvider.loadDefaultTool();
    }
    myArea.setCursor(null);
  }
}