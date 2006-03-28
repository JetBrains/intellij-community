/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.popup.*;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.*;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.GroupItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.awt.Point;

/**
 * @author yole
 */
public class CreateComponentAction extends AbstractGuiEditorAction {
  private ComponentItem myLastCreatedComponent = null;

  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    PaletteListPopupStep step = new PaletteListPopupStep(editor, getCreateLocation(editor, selection));
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
      if (container.isGrid()) {
        GridLayoutManager grid = (GridLayoutManager) container.getLayout();
        GridConstraints c = component.getConstraints();
        // try to insert in empty cell to the right or below the component; if not found -
        // insert row below selected component
        if (c.getColumn() + c.getColSpan() < grid.getColumnCount() &&
            container.getComponentAtGrid(c.getRow(), c.getColumn() + c.getColSpan()) == null) {
          dropLocation = new GridDropLocation(container, c.getRow(), c.getColumn() + c.getColSpan());
        }
        else if (c.getRow() + c.getRowSpan() < grid.getRowCount() &&
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

  private class PaletteListPopupStep implements ListPopupStep<ComponentItem>, SpeedSearchFilter<ComponentItem> {
    private ArrayList<ComponentItem> myItems = new ArrayList<ComponentItem>();
    private final GuiEditor myEditor;
    private final DropLocation myCreateLocation;

    PaletteListPopupStep(GuiEditor editor, final DropLocation createLocation) {
      myEditor = editor;
      myCreateLocation = createLocation;
      Palette palette = Palette.getInstance(editor.getProject());
      for(GroupItem group: palette.getToolWindowGroups()) {
        Collections.addAll(myItems, group.getItems());
      }
    }

    public List<ComponentItem> getValues() {
      return myItems;
    }

    public boolean isSelectable(final ComponentItem value) {
      return true;
    }

    public Icon getIconFor(final ComponentItem aValue) {
      return aValue.getSmallIcon();
    }

    @NotNull
    public String getTextFor(final ComponentItem value) {
      if (value.isAnyComponent()) {
        return UIDesignerBundle.message("palette.non.palette.component");
      }
      return value.getClassShortName();
    }

    public ListSeparator getSeparatorAbove(final ComponentItem value) {
      return null;
    }

    public int getDefaultOptionIndex() {
      if (myLastCreatedComponent != null) {
        int index = myItems.indexOf(myLastCreatedComponent);
        if (index >= 0) {
          return index;
        }
      }
      return 0;
    }

    public String getTitle() {
      return UIDesignerBundle.message("create.component.title");
    }

    public PopupStep onChosen(final ComponentItem selectedValue, final boolean finalChoice) {
      myLastCreatedComponent = selectedValue;
      myEditor.getMainProcessor().startInsertProcessor(selectedValue, myCreateLocation);
      return PopupStep.FINAL_CHOICE;
    }

    public boolean hasSubstep(final ComponentItem selectedValue) {
      return false;
    }

    public void canceled() {
    }

    public boolean isMnemonicsNavigationEnabled() {
      return false;
    }

    public MnemonicNavigationFilter<ComponentItem> getMnemonicNavigationFilter() {
      return null;
    }

    public boolean isSpeedSearchEnabled() {
      return true;
    }

    public boolean isAutoSelectionEnabled() {
      return false;
    }

    public SpeedSearchFilter<ComponentItem> getSpeedSearchFilter() {
      return this;
    }

    public boolean canBeHidden(final ComponentItem value) {
      return true;
    }

    public String getIndexedString(final ComponentItem value) {
      if (value.isAnyComponent()) {
        return "";
      }
      return value.getClassShortName();
    }
  }
}
