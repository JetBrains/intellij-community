package com.intellij.uiDesigner;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.openapi.diagnostic.Logger;

import java.util.Collections;
import java.util.Comparator;
import java.util.ArrayList;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

import javax.swing.*;

import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 07.11.2005
 * Time: 17:56:49
 * To change this template use File | Settings | File Templates.
 */
public class DraggedComponentList implements Transferable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.DraggedComponentList");

  private static DataFlavor ourDataFlavor;

  static {
    try {
      ourDataFlavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType);
    } catch (ClassNotFoundException e) {
      LOG.error(e);
    }
  }

  private ArrayList<RadComponent> mySelection;
  private GridConstraints[] myOriginalConstraints;
  private Rectangle[] myOriginalBounds;
  private RadContainer[] myOriginalParents;
  private int myDragRelativeColumn;
  private int myDragDeltaX;
  private int myDragDeltaY;

  private DraggedComponentList(final GuiEditor editor, final int x, final int y) {
    // Store selected components
    mySelection = FormEditingUtil.getSelectedComponents(editor);
    // sort selection in correct grid order
    Collections.sort(mySelection, new Comparator<RadComponent>() {
      public int compare(final RadComponent o1, final RadComponent o2) {
        if (o1.getParent() == o2.getParent()) {
          int result = o1.getConstraints().getRow() - o2.getConstraints().getRow();
          if (result == 0) {
            result = o1.getConstraints().getColumn() - o2.getConstraints().getColumn();
          }
          return result;
        }
        return 0;
      }
    });

    // Store original constraints and parents. This information is required
    // to restore initial state if drag is canceled.
    myOriginalConstraints = new GridConstraints[mySelection.size()];
    myOriginalBounds = new Rectangle[mySelection.size()];
    myOriginalParents = new RadContainer[mySelection.size()];
    for (int i = 0; i < mySelection.size(); i++) {
      final RadComponent component = mySelection.get(i);
      myOriginalConstraints[i] = component.getConstraints().store();
      myOriginalBounds[i] = component.getBounds();
      myOriginalParents[i] = component.getParent();
    }

    // It's very important to get component under mouse before the components are
    // removed from their parents.
    final RadComponent componentUnderMouse = FormEditingUtil.getRadComponentAt(editor, x, y);

    myDragRelativeColumn = 0;
    if (mySelection.size() > 1) {
      boolean sameRow = true;
      for(int i=1; i<myOriginalParents.length; i++) {
        if (myOriginalParents [i] != myOriginalParents [0] ||
            myOriginalConstraints [i].getRow() != myOriginalConstraints [0].getRow()) {
          sameRow = false;
          break;
        }
      }
      if (sameRow) {
        for(GridConstraints constraints: myOriginalConstraints) {
          myDragRelativeColumn = Math.max(myDragRelativeColumn,
                                          componentUnderMouse.getConstraints().getColumn() - constraints.getColumn());
        }
      }
    }

    for(RadComponent c: mySelection) {
      JComponent delegee = c.getDelegee();
      if (c == componentUnderMouse) {
        if (delegee.getX() > x && delegee.getX() + delegee.getWidth() < x) {
          myDragDeltaX = x - (delegee.getX() + delegee.getWidth() / 2);
        }
        if (delegee.getY() > y && delegee.getY() + delegee.getHeight() < y) {
          myDragDeltaY = y - (delegee.getY() + delegee.getHeight() / 2);
        }
      }
    }
  }

  public static DraggedComponentList pickupSelection(final GuiEditor editor, final int x, final int y) {
    return new DraggedComponentList(editor, x, y);
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

  public RadContainer getOriginalParent(final RadComponent c) {
    return myOriginalParents [mySelection.indexOf(c)];
  }

  public GridConstraints getOriginalConstraints(final RadComponent c) {
    return myOriginalConstraints [mySelection.indexOf(c)];
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

  public int getDragRelativeColumn() {
    return myDragRelativeColumn;
  }

  public GridConstraints[] getOriginalConstraints() {
    return myOriginalConstraints;
  }

  public RadContainer[] getOriginalParents() {
    return myOriginalParents;
  }
}
