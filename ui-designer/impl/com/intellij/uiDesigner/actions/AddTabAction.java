/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.designSurface.InsertComponentProcessor;
import com.intellij.uiDesigner.designSurface.DropLocation;
import com.intellij.uiDesigner.designSurface.ComponentItemDragObject;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadTabbedPane;
import com.intellij.uiDesigner.palette.Palette;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class AddTabAction extends AbstractGuiEditorAction {
  public AddTabAction() {
    super(true);
  }

  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    RadTabbedPane tabbedPane = (RadTabbedPane) selection.get(0);
    Palette palette = Palette.getInstance(editor.getProject());

    final RadComponent radComponent = InsertComponentProcessor.createPanelComponent(editor);
    final DropLocation dropLocation = tabbedPane.getDropLocation(null);
    dropLocation.processDrop(editor, new RadComponent[] { radComponent }, null, 
                             new ComponentItemDragObject(palette.getPanelItem()));
  }

  @Override protected void update(@NotNull GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    e.getPresentation().setVisible(selection.size() == 1 && selection.get(0) instanceof RadTabbedPane);
  }
}
