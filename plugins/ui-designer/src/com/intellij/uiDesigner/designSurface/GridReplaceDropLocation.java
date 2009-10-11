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

import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.FormEditingUtil;

import java.util.Collections;

/**
 * @author yole
 */
public class GridReplaceDropLocation extends GridDropLocation {
  public GridReplaceDropLocation(final RadContainer container, final int row, final int column) {
    super(container, row, column);
  }

  @Override
  public boolean canDrop(final ComponentDragObject dragObject) {
    if (dragObject.getComponentCount() != 1) return false;
    return super.canDrop(dragObject);    //To change body of overridden methods use File | Settings | File Templates.
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
