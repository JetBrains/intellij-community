// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class MoveComponentAction extends AbstractGuiEditorAction {
  private final int myRowDelta;
  private final int myColumnDelta;
  private final int myRowSpanDelta;
  private final int myColSpanDelta;

  public MoveComponentAction(final int rowDelta, final int columnDelta, final int rowSpanDelta, final int colSpanDelta) {
    super(true);
    myRowDelta = rowDelta;
    myColumnDelta = columnDelta;
    myRowSpanDelta = rowSpanDelta;
    myColSpanDelta = colSpanDelta;
  }

  @Override
  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    if (myColumnDelta != 0) {
      // sort the selection so that move in indexed layout will handle components in correct order
      Collections.sort(selection, (o1, o2) -> {
        int index1 = o1.getParent().indexOfComponent(o1);
        int index2 = o2.getParent().indexOfComponent(o2);
        return (index2 - index1) * myColumnDelta;
      });
    }
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
