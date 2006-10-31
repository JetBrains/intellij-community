/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class MoveComponentAction extends AbstractGuiEditorAction {
  private int myRowDelta;
  private int myColumnDelta;
  private final int myRowSpanDelta;
  private final int myColSpanDelta;

  public MoveComponentAction(final int rowDelta, final int columnDelta, final int rowSpanDelta, final int colSpanDelta) {
    super(true);
    myRowDelta = rowDelta;
    myColumnDelta = columnDelta;
    myRowSpanDelta = rowSpanDelta;
    myColSpanDelta = colSpanDelta;
  }

  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    for(RadComponent c: selection) {
      c.getParent().getLayoutManager().moveComponent(c, myRowDelta, myColumnDelta, myRowSpanDelta, myColSpanDelta);
    }
  }

  @Override
  protected void update(@NotNull GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    e.getPresentation().setEnabled(true);
    for(RadComponent c: selection) {
      if (!c.getParent().getLayoutManager().canMoveComponent(c, myRowDelta, myColumnDelta, myRowSpanDelta, myColSpanDelta)) {
        e.getPresentation().setEnabled(false);
        return;
      }
    }
  }
}
