/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.designSurface;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.Component;
import java.awt.Point;
import java.awt.dnd.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class DragSelectionProcessor extends EventProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.designSurface.DragSelectionProcessor");

  /**
   * We have not start drag/cancel drop if mouse pointer trembles in small area
   */
  public static final int TREMOR = 3;

  private final GuiEditor myEditor;

  private Point myPressPoint;

  private boolean myDragStarted;

  private final MyDragGestureRecognizer myDragGestureRecognizer;
  private final MyDragSourceListener myDragSourceListener = new MyDragSourceListener();

  public DragSelectionProcessor(@NotNull final GuiEditor editor) {
    myEditor = editor;
    myDragGestureRecognizer = new MyDragGestureRecognizer(DragSource.getDefaultDragSource(),
                                                          myEditor.getActiveDecorationLayer(),
                                                          DnDConstants.ACTION_COPY_OR_MOVE);
  }

  public boolean isDragActive() {
    return myDragStarted;
  }

  protected boolean cancelOperation() {
    if (!myDragStarted) {
      return true;
    }
    // Try to drop selection at the point of mouse event.
    //cancelDrag();
    myEditor.setDesignTimeInsets(2);
    myEditor.getActiveDecorationLayer().removeFeedback();
    myEditor.repaintLayeredPane();
    return true;
  }

  protected void processKeyEvent(final KeyEvent e) {
  }

  protected void processMouseEvent(final MouseEvent e) {
    if (e.getID() == MouseEvent.MOUSE_PRESSED) {
      myPressPoint = e.getPoint();
    }
    else if (e.getID() == MouseEvent.MOUSE_RELEASED) {
      if (!myDragStarted) {
        RadComponent component = FormEditingUtil.getRadComponentAt(myEditor.getRootContainer(), e.getX(), e.getY());
        if (component != null) {
          if (UIUtil.isControlKeyDown(e)) {
            component.setSelected(!component.isSelected());
          }
        }
      }
    }
    else if (e.getID() == MouseEvent.MOUSE_DRAGGED) {
      if (!myDragStarted) {
        if ((Math.abs(e.getX() - myPressPoint.getX()) > TREMOR || Math.abs(e.getY() - myPressPoint.getY()) > TREMOR)) {
          ArrayList<InputEvent> eventList = new ArrayList<>();
          eventList.add(e);
          myDragGestureRecognizer.setTriggerEvent(e);
          DragGestureEvent dge = new DragGestureEvent(myDragGestureRecognizer,
                                                      UIUtil.isControlKeyDown(e) ? DnDConstants.ACTION_COPY : DnDConstants.ACTION_MOVE,
                                                      myPressPoint, eventList);

          myDragStarted = true;
          myEditor.getDropTargetListener().setUseDragDelta(true);
          dge.startDrag(null,
                        DraggedComponentList.pickupSelection(myEditor, e.getPoint()),
                        myDragSourceListener);
        }
      }
    }
  }

  private static class MyDragGestureRecognizer extends DragGestureRecognizer {
    public MyDragGestureRecognizer(DragSource ds, Component c, int sa) {
      super(ds, c, sa);
    }

    protected void registerListeners() {
    }

    protected void unregisterListeners() {
    }

    public void setTriggerEvent(final MouseEvent e) {
      resetRecognizer();
      appendEvent(e);
    }
  }

  private class MyDragSourceListener extends DragSourceAdapter {
    public void dropActionChanged(DragSourceDragEvent dsde) {
      final int shiftDownMask = (dsde.getGestureModifiersEx() & KeyEvent.SHIFT_DOWN_MASK);
      if (shiftDownMask != 0) {
        myEditor.setDesignTimeInsets(12);
      }
      else {
        myEditor.setDesignTimeInsets(2);
      }
    }

    public void dragDropEnd(DragSourceDropEvent dsde) {
      myDragStarted = false;
      myEditor.getDropTargetListener().setUseDragDelta(false);
      myEditor.setDesignTimeInsets(2);
    }
  }
}
