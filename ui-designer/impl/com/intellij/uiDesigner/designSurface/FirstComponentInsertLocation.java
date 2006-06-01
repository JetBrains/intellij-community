/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.HSpacer;
import com.intellij.uiDesigner.VSpacer;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Dimension;

/**
 * @author yole
 */
public class FirstComponentInsertLocation extends GridDropLocation {
  protected final Rectangle myCellRect;
  protected final int myXPart;
  protected final int myYPart;

  public FirstComponentInsertLocation(final RadContainer container,
                                      final int row,
                                      final int column,
                                      final Point targetPoint,
                                      @NotNull final Rectangle cellRect) {
    super(container, row, column);
    myCellRect = cellRect;
    int midX1 = myCellRect.x + myCellRect.width / 3;
    int midX2 = myCellRect.x + (myCellRect.width*2) / 3;
    int midY1 = myCellRect.y + myCellRect.height / 3;
    int midY2 = myCellRect.y + (myCellRect.height*2) / 3;
    if (targetPoint.x < midX1) {
      myXPart = 0;
    }
    else if (targetPoint.x < midX2) {
      myXPart = 1;
    }
    else {
      myXPart = 2;
    }
    if (targetPoint.y < midY1) {
      myYPart = 0;
    }
    else if (targetPoint.y < midY2) {
      myYPart = 1;
    }
    else {
      myYPart = 2;
    }
  }


  public FirstComponentInsertLocation(final RadContainer container,
                                      final int row,
                                      final int column,
                                      final Rectangle cellRect,
                                      final int xPart,
                                      final int yPart) {
    super(container, row, column);
    myCellRect = cellRect;
    myXPart = xPart;
    myYPart = yPart;
  }

  @Override public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
    int midX1 = myCellRect.x + myCellRect.width / 3;
    int midX2 = myCellRect.x + (myCellRect.width*2) / 3;
    int midY1 = myCellRect.y + myCellRect.height / 3;
    int midY2 = myCellRect.y + (myCellRect.height*2) / 3;

    Rectangle rc = new Rectangle();
    if (myXPart == 0) {
      rc.x = myCellRect.x;
      rc.width = initialWidth(dragObject, midX1 - myCellRect.x);
    }
    else if (myXPart == 1) {
      if (!isInsertTwoSpacers(dragObject.getHSizePolicy())) {
        rc.x = myCellRect.x;
        rc.width = myCellRect.width;
      }
      else {
        rc.x = midX1;
        rc.width = midX2 - midX1;
      }
    }
    else {
      rc.width = initialWidth(dragObject, myCellRect.width - (midX2 - myCellRect.x));
      rc.x = myCellRect.width - rc.width;
    }

    if (myYPart == 0) {
      rc.y = myCellRect.y;
      rc.height = initialHeight(dragObject, midY1 - myCellRect.y);
    }
    else if (myYPart == 1) {
      if (!isInsertTwoSpacers(dragObject.getVSizePolicy())) {
        rc.y = myCellRect.y;
        rc.height = myCellRect.height;
      }
      else {
        rc.y = midY1;
        rc.height = midY2 - midY1;
      }
    }
    else {
      rc.height = initialHeight(dragObject, myCellRect.height - (midY2 - myCellRect.y));
      rc.y = myCellRect.height - rc.height;
    }

    feedbackLayer.putFeedback(myContainer.getDelegee(), rc);
  }

  private static boolean isInsertTwoSpacers(int sizePolicy) {
    // return (sizePolicy & GridConstraints.SIZEPOLICY_WANT_GROW) != 0;
    return false;
  }

  private int initialWidth(ComponentDragObject dragObject, int defaultSize) {
    Dimension initialSize = dragObject.getInitialSize(getContainer().getDelegee());
    if (initialSize.width > 0 && initialSize.width < defaultSize) {
      return initialSize.width;
    }
    return defaultSize;
  }

  private int initialHeight(ComponentDragObject dragObject, int defaultSize) {
    Dimension initialSize = dragObject.getInitialSize(getContainer().getDelegee());
    if (initialSize.height > 0 && initialSize.height < defaultSize) {
      return initialSize.height;
    }
    return defaultSize;
  }

  @Override public void processDrop(final GuiEditor editor,
                                    final RadComponent[] components,
                                    final GridConstraints[] constraintsToAdjust,
                                    final ComponentDragObject dragObject) {
    super.processDrop(editor, components, constraintsToAdjust, dragObject);

    Palette palette = Palette.getInstance(editor.getProject());
    ComponentItem hSpacerItem = palette.getItem(HSpacer.class.getName());
    ComponentItem vSpacerItem = palette.getItem(VSpacer.class.getName());

    int hSizePolicy = getSizePolicy(components, true);
    int vSizePolicy = getSizePolicy(components, false);

    InsertComponentProcessor icp = new InsertComponentProcessor(editor);

    if (myXPart == 0 ||
        (myXPart == 1 && isInsertTwoSpacers(hSizePolicy))) {
      insertSpacer(icp, hSpacerItem, GridInsertMode.ColumnAfter);
    }
    if (myXPart == 2 ||
        (myXPart == 1 && isInsertTwoSpacers(hSizePolicy))) {
      insertSpacer(icp, hSpacerItem, GridInsertMode.ColumnBefore);
    }

    if (myYPart == 0 ||
        (myYPart == 1 && isInsertTwoSpacers(vSizePolicy))) {
      insertSpacer(icp, vSpacerItem, GridInsertMode.RowAfter);
    }
    if (myYPart == 2 ||
        (myYPart == 1 && isInsertTwoSpacers(vSizePolicy))) {
      insertSpacer(icp, vSpacerItem, GridInsertMode.RowBefore);
    }
  }

  @Nullable
  public DropLocation getAdjacentLocation(Direction direction) {
    if (direction == Direction.DOWN && myYPart < 2) {
      return createAdjacentLocation(myXPart, myYPart+1);
    }
    if (direction == Direction.UP && myYPart > 0) {
      return createAdjacentLocation(myXPart, myYPart-1);
    }
    if (direction == Direction.RIGHT && myXPart < 2) {
      return createAdjacentLocation(myXPart+1, myYPart);
    }
    if (direction == Direction.LEFT && myXPart > 0) {
      return createAdjacentLocation(myXPart-1, myYPart);
    }
    return null;
  }

  protected FirstComponentInsertLocation createAdjacentLocation(final int xPart, final int yPart) {
    return new FirstComponentInsertLocation(myContainer, myRow, myColumn, myCellRect, xPart, yPart);
  }

  private void insertSpacer(InsertComponentProcessor icp, ComponentItem spacerItem, GridInsertMode mode) {
    GridInsertLocation location = new GridInsertLocation(myContainer, 0, 0, mode);
    icp.processComponentInsert(spacerItem, location);
  }

  private static int getSizePolicy(final RadComponent[] components, final boolean horizontal) {
    int result = 0;
    for(RadComponent component: components) {
      GridConstraints c = component.getConstraints();
      result |= horizontal ? c.getHSizePolicy() : c.getVSizePolicy();
    }
    return result;
  }
}
