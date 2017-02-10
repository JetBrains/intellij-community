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
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

/**
 * @author yole
 */
public class DraggedComponentList implements Transferable, ComponentDragObject {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.DraggedComponentList");

  private static DataFlavor ourDataFlavor;

  static {
    try {
      ourDataFlavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType);
    } catch (ClassNotFoundException e) {
      LOG.error(e);
    }
  }

  private final ArrayList<RadComponent> mySelection;
  private GridConstraints[] myOriginalConstraints;
  private Rectangle[] myOriginalBounds;
  private RadContainer[] myOriginalParents;
  private int myDragRelativeColumn = 0;
  private int myComponentUnderMouseColumn;
  private int myComponentUnderMouseRow;
  private int myDragDeltaX = 0;
  private int myDragDeltaY = 0;
  private boolean myHasDragDelta = false;

  private DraggedComponentList(Collection<RadComponent> selection) {
    mySelection = new ArrayList<>(selection);
    fillOriginalConstraints();
  }

  private DraggedComponentList(final GuiEditor editor, final Point pnt) {
    // Store selected components
    mySelection = FormEditingUtil.getSelectedComponents(editor);

    // sort selection in correct grid order
    Collections.sort(mySelection, (o1, o2) -> {
      if (o1.getParent() == o2.getParent()) {
        int result = o1.getConstraints().getRow() - o2.getConstraints().getRow();
        if (result == 0) {
          result = o1.getConstraints().getColumn() - o2.getConstraints().getColumn();
        }
        return result;
      }
      return 0;
    });

    RadComponent componentUnderMouse = null;
    int componentUnderMouseIndex = mySelection.size() == 0 ? -1 : 0;
    if (pnt != null) {
      for(int i=0; i<mySelection.size(); i++) {
        RadComponent c = mySelection.get(i);
        Point aPoint = SwingUtilities.convertPoint(editor.getRootContainer().getDelegee(), pnt,
                                                   c.getParent().getDelegee());
        if (c.getBounds().contains(aPoint)) {
          aPoint = SwingUtilities.convertPoint(editor.getRootContainer().getDelegee(), pnt,
                                               c.getDelegee());
          mySelection.set(i, c.getComponentToDrag(aPoint));
          componentUnderMouseIndex = i;
        }
      }
    }
    fillOriginalConstraints();

    if (componentUnderMouseIndex >= 0) {
      componentUnderMouse = mySelection.get(componentUnderMouseIndex);
      myComponentUnderMouseColumn = myOriginalConstraints [componentUnderMouseIndex].getColumn();
      myComponentUnderMouseRow = myOriginalConstraints [componentUnderMouseIndex].getRow();
    }

    LOG.debug("myComponentUnderMouseColumn=" + myComponentUnderMouseColumn +
              ", myComponentUnderMouseRow=" + myComponentUnderMouseRow);

    if (mySelection.size() > 1 && componentUnderMouse != null) {
      for(GridConstraints constraints: myOriginalConstraints) {
        myDragRelativeColumn = Math.max(myDragRelativeColumn,
                                        componentUnderMouse.getConstraints().getColumn() - constraints.getColumn());
      }
    }

     for(RadComponent c: mySelection) {
      JComponent delegee = c.getDelegee();
      if (c == componentUnderMouse && pnt != null) {
        if (delegee.getX() > pnt.x && delegee.getX() + delegee.getWidth() < pnt.x) {
          myDragDeltaX = pnt.x - (delegee.getX() + delegee.getWidth() / 2);
        }
        if (delegee.getY() > pnt.y && delegee.getY() + delegee.getHeight() < pnt.y) {
          myDragDeltaY = pnt.y - (delegee.getY() + delegee.getHeight() / 2);
        }
        myHasDragDelta = true;
      }
    }
  }

  private void fillOriginalConstraints() {
    // Store original constraints and parents. This information is required
    // to restore initial state if drag is canceled.
    myOriginalConstraints = new GridConstraints[mySelection.size()];
    myOriginalBounds = new Rectangle[mySelection.size()];
    myOriginalParents = new RadContainer[mySelection.size()];
    for (int i1 = 0; i1 < mySelection.size(); i1++) {
      final RadComponent component = mySelection.get(i1);
      myOriginalConstraints[i1] = component.getConstraints().store();
      myOriginalBounds[i1] = component.getBounds();
      myOriginalParents[i1] = component.getParent();
    }
  }

  public static DraggedComponentList pickupSelection(final GuiEditor editor, @Nullable Point pnt) {
    return new DraggedComponentList(editor, pnt);
  }

  public static DraggedComponentList withComponents(RadComponent... c) {
    ArrayList<RadComponent> list = new ArrayList<>();
    Collections.addAll(list, c);
    return new DraggedComponentList(list);
  }

  @Nullable
  public static DraggedComponentList fromTransferable(final Transferable transferable) {
    if (transferable.isDataFlavorSupported(ourDataFlavor)) {
      Object data;
      try {
        data = transferable.getTransferData(ourDataFlavor);
      }
      catch (Exception e) {
        return null;
      }
      if (data instanceof DraggedComponentList) {
        return (DraggedComponentList) data;
      }
    }
    return null;
  }

  public int getDragDeltaX() {
    return myDragDeltaX;
  }

  public int getDragDeltaY() {
    return myDragDeltaY;
  }

  public ArrayList<RadComponent> getComponents() {
    return mySelection;
  }

  public int getComponentCount() {
    return mySelection.size();
  }

  public RadContainer getOriginalParent(final RadComponent c) {
    return myOriginalParents [mySelection.indexOf(c)];
  }

  /**
   * Returns a copy of the original constraints array.
   */
  public GridConstraints[] getOriginalConstraints() {
    GridConstraints[] result = new GridConstraints[myOriginalConstraints.length];
    for(int i=0; i<myOriginalConstraints.length; i++) {
      result [i] = myOriginalConstraints [i].store();
    }
    return result;
  }

  public Rectangle[] getOriginalBounds() {
    return myOriginalBounds;
  }

  public Rectangle getOriginalBounds(final RadComponent c) {
    return myOriginalBounds [mySelection.indexOf(c)];
  }

  public DataFlavor[] getTransferDataFlavors() {
    return new DataFlavor[] { ourDataFlavor };
  }

  public boolean isDataFlavorSupported(DataFlavor flavor) {
    return flavor.equals(ourDataFlavor);
  }

  public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
    return this;
  }

  public boolean isHGrow() {
    for(GridConstraints c: myOriginalConstraints) {
      if ((c.getHSizePolicy() & GridConstraints.SIZEPOLICY_WANT_GROW) != 0) return true;
    }
    return false;
  }

  public boolean isVGrow() {
    for(GridConstraints c: myOriginalConstraints) {
      if ((c.getVSizePolicy() & GridConstraints.SIZEPOLICY_WANT_GROW) != 0) return true;
    }
    return false;
  }

  public int getRelativeRow(int componentIndex) {
    return myOriginalConstraints [componentIndex].getRow() - myComponentUnderMouseRow;
  }

  public int getRelativeCol(int componentIndex) {
    return myOriginalConstraints [componentIndex].getColumn() - myComponentUnderMouseColumn;
  }

  public int getRowSpan(int componentIndex) {
    return myOriginalConstraints [componentIndex].getRowSpan();
  }

  public int getColSpan(int componentIndex) {
    return myOriginalConstraints [componentIndex].getColSpan();
  }

  public Point getDelta(int componentIndex) {
    return null;
  }

  @NotNull
  public Dimension getInitialSize(final RadContainer targetContainer) {
    if (myOriginalBounds.length == 1) {
      return myOriginalBounds [0].getSize();
    }
    return new Dimension(-1, -1);
  }

  public RadContainer[] getOriginalParents() {
    return myOriginalParents;
  }

  public boolean hasDragDelta() {
    return myHasDragDelta;
  }
}
