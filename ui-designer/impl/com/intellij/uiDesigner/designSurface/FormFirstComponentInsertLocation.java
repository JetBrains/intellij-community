/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadAbstractGridLayoutManager;
import com.intellij.uiDesigner.core.GridConstraints;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.RowSpec;

import java.awt.Point;
import java.awt.Rectangle;

/**
 * FirstComponentInsertLocation customized for JGoodies Forms.
 *
 * @author yole
 */
public class FormFirstComponentInsertLocation extends FirstComponentInsertLocation {
  public FormFirstComponentInsertLocation(final RadContainer container, final Point targetPoint, final Rectangle cellRect) {
    super(container, targetPoint, cellRect);
  }


  public FormFirstComponentInsertLocation(RadContainer container, Rectangle cellRect, int xPart, int yPart) {
    super(container, cellRect, xPart, yPart);
  }

  @Override
  protected FirstComponentInsertLocation createAdjacentLocation(final int xPart, final int yPart) {
    return new FormFirstComponentInsertLocation(myContainer, myCellRect, xPart, yPart);
  }

  @Override
  public void processDrop(final GuiEditor editor, final RadComponent[] components, final GridConstraints[] constraintsToAdjust,
                          final ComponentDragObject dragObject) {
    RadAbstractGridLayoutManager gridLayout = myContainer.getGridLayoutManager();
    if (myContainer.getGridRowCount() == 0 && myContainer.getGridColumnCount() == 0) {
      gridLayout.insertGridCells(myContainer, 0, false, true, true);
      gridLayout.insertGridCells(myContainer, 0, true, true, true);
    }
    dropIntoGrid(myContainer, components, myRow, myColumn, dragObject);

    FormLayout formLayout = (FormLayout) myContainer.getDelegee().getLayout();
    if (myXPart == 0) {
      formLayout.setColumnSpec(1, new ColumnSpec("d"));
    }
    else if (myXPart == 2) {
      gridLayout.insertGridCells(myContainer, 0, false, true, true);
    }
    if (myYPart == 0) {
      formLayout.setRowSpec(1, new RowSpec("d"));
    }
    else if (myYPart == 2) {
      gridLayout.insertGridCells(myContainer, 0, true, true, true);
    }
  }
}
