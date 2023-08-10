// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.FormEditingUtil;

import java.util.Collections;


public class GridReplaceDropLocation extends GridDropLocation {
  public GridReplaceDropLocation(final RadContainer container, final int row, final int column) {
    super(container, row, column);
  }

  @Override
  public boolean canDrop(final ComponentDragObject dragObject) {
    if (dragObject.getComponentCount() != 1) return false;
    return super.canDrop(dragObject);
  }

  @Override
  protected RadComponent findOverlappingComponent(final int startRow, final int startCol, final int rowSpan, final int colSpan) {
    if (rowSpan == 1 && colSpan == 1) {
      // we will replace the overlapping component, so it shouldn't prevent drop
      return null;
    }
    return super.findOverlappingComponent(startRow, startCol, rowSpan, colSpan);
  }

  @Override
  public void processDrop(final GuiEditor editor,
                          final RadComponent[] components,
                          final GridConstraints[] constraintsToAdjust,
                          final ComponentDragObject dragObject) {
    RadComponent c = myContainer.getComponentAtGrid(myRow, myColumn);
    if (c != null) {
      FormEditingUtil.deleteComponents(Collections.singletonList(c), false);
    }
    super.processDrop(editor, components, constraintsToAdjust, dragObject);
  }
}
