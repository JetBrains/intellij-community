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

/**
 * @author yole
 */
public class FirstComponentInsertLocation extends GridDropLocation {
  private Point myTargetPoint;
  private Rectangle myCellRect;

  public FirstComponentInsertLocation(final RadContainer container,
                                      final int row,
                                      final int column,
                                      final Point targetPoint,
                                      @NotNull final Rectangle cellRect) {
    super(container, row, column);
    myTargetPoint = targetPoint;
    myCellRect = cellRect;
  }

  @Override public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
    int midX1 = myCellRect.x + myCellRect.width / 3;
    int midX2 = myCellRect.x + (myCellRect.width*2) / 3;
    int midY1 = myCellRect.y + myCellRect.height / 3;
    int midY2 = myCellRect.y + (myCellRect.height*2) / 3;

    Rectangle rc = new Rectangle();
    if (myTargetPoint.x < midX1) {
      rc.x = myCellRect.x;
      rc.width = midX1 - myCellRect.x;
    }
    else if (myTargetPoint.x < midX2) {
      if ((dragObject.getHSizePolicy() & GridConstraints.SIZEPOLICY_WANT_GROW) != 0) {
        rc.x = myCellRect.x;
        rc.width = myCellRect.width;
      }
      else {
        rc.x = midX1;
        rc.width = midX2 - midX1;
      }
    }
    else {
      rc.x = midX2;
      rc.width = myCellRect.width - (midX2 - myCellRect.x);
    }

    if (myTargetPoint.y < midY1) {
      rc.y = myCellRect.y;
      rc.height = midY1 - myCellRect.y;
    }
    else if (myTargetPoint.y < midY2) {
      if ((dragObject.getVSizePolicy() & GridConstraints.SIZEPOLICY_WANT_GROW) != 0) {
        rc.y = myCellRect.y;
        rc.height = myCellRect.height;
      }
      else {
        rc.y = midY1;
        rc.height = midY2 - midY1;
      }
    }
    else {
      rc.y = midY2;
      rc.height = myCellRect.height - (midY2 - myCellRect.y);
    }

    feedbackLayer.putFeedback(myContainer.getDelegee(), rc);
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

    int midX1 = myCellRect.x + myCellRect.width / 3;
    int midX2 = myCellRect.x + (myCellRect.width*2) / 3;
    int midY1 = myCellRect.y + myCellRect.height / 3;
    int midY2 = myCellRect.y + (myCellRect.height*2) / 3;

    InsertComponentProcessor icp = new InsertComponentProcessor(editor);

    if (myTargetPoint.x < midX1 ||
        (myTargetPoint.x < midX2 && (hSizePolicy & GridConstraints.SIZEPOLICY_WANT_GROW) == 0)) {
      insertSpacer(icp, hSpacerItem, GridInsertMode.ColumnAfter);
    }
    if (myTargetPoint.x > midX2 ||
        (myTargetPoint.x > midX1 && (hSizePolicy & GridConstraints.SIZEPOLICY_WANT_GROW) == 0)) {
      insertSpacer(icp, hSpacerItem, GridInsertMode.ColumnBefore);
    }

    if (myTargetPoint.y < midY1 ||
        (myTargetPoint.y < midY2 && (vSizePolicy & GridConstraints.SIZEPOLICY_WANT_GROW) == 0)) {
      insertSpacer(icp, vSpacerItem, GridInsertMode.RowAfter);
    }
    if (myTargetPoint.y > midY2 ||
        (myTargetPoint.y > midY1 && (vSizePolicy & GridConstraints.SIZEPOLICY_WANT_GROW) == 0)) {
      insertSpacer(icp, vSpacerItem, GridInsertMode.RowBefore);
    }
  }

  @Nullable
  public DropLocation getAdjacentLocation(Direction direction) {
    return null;
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
