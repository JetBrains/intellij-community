/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.*;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.util.Processor;

import java.awt.Point;
import java.util.List;

/**
 * @author yole
 */
public class CreateComponentAction extends AbstractGuiEditorAction {
  private ComponentItem myLastCreatedComponent = null;

  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    Processor<ComponentItem> processor = new Processor<ComponentItem>() {
      public boolean process(final ComponentItem selectedValue) {
        myLastCreatedComponent = selectedValue;
        editor.getMainProcessor().startInsertProcessor(selectedValue, getCreateLocation(editor, selection));
        return true;
      }
    };

    PaletteListPopupStep step = new PaletteListPopupStep(editor, myLastCreatedComponent, processor,
                                                         UIDesignerBundle.message("create.component.title"));
    final ListPopup listPopup = JBPopupFactory.getInstance().createWizardStep(step);

    if (selection.size() > 0) {
      listPopup.showUnderneathOf(selection.get(0).getDelegee());
    }
    else {
      listPopup.showInCenterOf(editor.getRootContainer().getDelegee());
    }
  }

  private static DropLocation getCreateLocation(final GuiEditor editor, final List<RadComponent> selection) {
    DropLocation dropLocation = null;
    if (selection.size() > 0) {
      RadComponent component = selection.get(0);
      final RadContainer container = component.getParent();
      if (container.getLayoutManager().isGrid()) {
        GridConstraints c = component.getConstraints();
        // try to insert in empty cell to the right or below the component; if not found -
        // insert row below selected component
        if (c.getColumn() + c.getColSpan() < container.getGridColumnCount() &&
            container.getComponentAtGrid(c.getRow(), c.getColumn() + c.getColSpan()) == null) {
          dropLocation = new GridDropLocation(container, c.getRow(), c.getColumn() + c.getColSpan());
        }
        else if (c.getRow() + c.getRowSpan() < container.getGridRowCount() &&
                 container.getComponentAtGrid(c.getRow() + c.getRowSpan(), c.getColumn()) == null) {
          dropLocation = new GridDropLocation(container, c.getRow() + c.getRowSpan(), c.getColumn());
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
        dropLocation = editor.getRootContainer().getDropLocation(null);
      }
    }
    return dropLocation;
  }
}
