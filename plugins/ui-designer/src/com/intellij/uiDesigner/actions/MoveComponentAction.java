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
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;

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

  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    if (myColumnDelta != 0) {
      // sort the selection so that move in indexed layout will handle components in correct order
      Collections.sort(selection, new Comparator<RadComponent>() {
        public int compare(final RadComponent o1, final RadComponent o2) {
          int index1 = o1.getParent().indexOfComponent(o1);
          int index2 = o2.getParent().indexOfComponent(o2);
          return (index2 - index1) * myColumnDelta;
        }
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
