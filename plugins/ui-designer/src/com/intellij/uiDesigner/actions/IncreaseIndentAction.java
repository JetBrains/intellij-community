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
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.properties.IndentProperty;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class IncreaseIndentAction extends AbstractGuiEditorAction {
  public IncreaseIndentAction() {
    super(true);
  }

  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    IndentProperty indentProperty = IndentProperty.getInstance(editor.getProject());
    for(RadComponent c: selection) {
      int indent = indentProperty.getValue(c).intValue();
      indentProperty.setValueEx(c, adjustIndent(indent));
    }
  }

  protected void update(@NotNull GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    final boolean applicable = canAdjustIndent(selection);
    e.getPresentation().setVisible(applicable);
    final Component focusOwner = IdeFocusManager.findInstanceByComponent(editor).getFocusOwner();
    e.getPresentation().setEnabled(applicable && (focusOwner == editor || editor.isAncestorOf(focusOwner)));
  }

  private boolean canAdjustIndent(final ArrayList<RadComponent> selection) {
    for(RadComponent c: selection) {
      if (canAdjustIndent(c)) {
        return true;
      }
    }
    return false;
  }

  protected int adjustIndent(final int indent) {
    return indent + 1;
  }

  protected boolean canAdjustIndent(final RadComponent component) {
    return component.getParent().getLayout() instanceof GridLayoutManager;
  }
}
