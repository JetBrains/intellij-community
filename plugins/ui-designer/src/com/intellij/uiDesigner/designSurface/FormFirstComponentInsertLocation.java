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

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadAbstractGridLayoutManager;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.RowSpec;

import java.awt.*;

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
    if (myContainer.getGridRowCount() == 0) {
      gridLayout.insertGridCells(myContainer, 0, true, true, true);
    }
    if (myContainer.getGridColumnCount() == 0) {
      gridLayout.insertGridCells(myContainer, 0, false, true, true);
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
