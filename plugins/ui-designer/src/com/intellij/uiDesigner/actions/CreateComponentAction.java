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

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.*;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.util.Processor;

import java.awt.*;
import java.util.List;

/**
 * @author yole
 */
public class CreateComponentAction extends AbstractGuiEditorAction {
  private ComponentItem myLastCreatedComponent = null;

  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    Processor<ComponentItem> processor = new Processor<ComponentItem>() {
      public boolean process(final ComponentItem selectedValue) {
        if (selectedValue != null) {
          myLastCreatedComponent = selectedValue;
          editor.getMainProcessor().startInsertProcessor(selectedValue, getCreateLocation(editor, selection));
        }
        return true;
      }
    };

    PaletteListPopupStep step = new PaletteListPopupStep(editor, myLastCreatedComponent, processor,
                                                         UIDesignerBundle.message("create.component.title"));
    final ListPopup listPopup = JBPopupFactory.getInstance().createListPopup(step);

    if (selection.size() > 0) {
      FormEditingUtil.showPopupUnderComponent(listPopup, selection.get(0));
    }
    else {
      listPopup.showInCenterOf(editor.getRootContainer().getDelegee());
    }
  }

  private static ComponentDropLocation getCreateLocation(final GuiEditor editor, final List<RadComponent> selection) {
    ComponentDropLocation dropLocation = null;
    if (selection.size() > 0) {
      RadComponent component = selection.get(0);
      final RadContainer container = component.getParent();
      if (container.getLayoutManager().isGrid()) {
        GridConstraints c = component.getConstraints();
        // try to insert in empty cell to the right or below the component; if not found -
        // insert row below selected component
        int nextCol = FormEditingUtil.adjustForGap(component.getParent(), c.getColumn() + c.getColSpan(), false, 1);
        int nextRow = FormEditingUtil.adjustForGap(component.getParent(), c.getRow() + c.getRowSpan(), true, 1);
        if (nextCol < container.getGridColumnCount() &&
            container.getComponentAtGrid(c.getRow(), nextCol) == null) {
          dropLocation = new GridDropLocation(container, c.getRow(), nextCol);
        }
        else if (nextRow < container.getGridRowCount() &&
                 container.getComponentAtGrid(nextRow, c.getColumn()) == null) {
          dropLocation = new GridDropLocation(container, nextRow, c.getColumn());
        }
        else {
          dropLocation = new GridInsertLocation(container, c.getRow() + c.getRowSpan() - 1, c.getColumn(),
                                                GridInsertMode.RowAfter);
        }
      }
    }
    if (dropLocation == null) {
      final Point mousePosition = editor.getMainProcessor().getLastMousePosition();
      if (mousePosition != null) {
        RadContainer container = GridInsertProcessor.getDropTargetContainer(editor.getRootContainer(), mousePosition);
        if (container == null) {
          container = editor.getRootContainer();
        }
        if (container instanceof RadRootContainer && container.getComponentCount() == 1 &&
          container.getComponent(0) instanceof RadContainer) {
          RadContainer childContainer = (RadContainer)container.getComponent(0);
          dropLocation = childContainer.getDropLocation(null);
        }
        else {
          dropLocation = GridInsertProcessor.getDropLocation(editor.getRootContainer(), mousePosition);
        }
      }
      else {
        final RadRootContainer container = editor.getRootContainer();
        if (container.getComponentCount() == 1 && container.getComponent(0) instanceof RadContainer) {
          RadContainer childContainer = (RadContainer)container.getComponent(0);
          dropLocation = childContainer.getDropLocation(null);
        }
        else {
          dropLocation = container.getDropLocation(null);
        }
      }
    }
    return dropLocation;
  }
}
