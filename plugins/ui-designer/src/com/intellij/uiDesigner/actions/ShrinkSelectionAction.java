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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.SelectionState;
import com.intellij.uiDesigner.componentTree.ComponentPtr;
import com.intellij.uiDesigner.componentTree.ComponentTreeBuilder;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager;

import java.util.Stack;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ShrinkSelectionAction extends AnAction{
  public void actionPerformed(final AnActionEvent e) {
    final GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
    assert editor != null;
    final SelectionState selectionState = editor.getSelectionState();
    selectionState.setInsideChange(true);
    ComponentTreeBuilder builder = DesignerToolWindowManager.getInstance(editor).getComponentTreeBuilder();
    builder.beginUpdateSelection();

    try{
      final Stack<ComponentPtr[]> history = selectionState.getSelectionHistory();
      history.pop();
      SelectionState.restoreSelection(editor, history.peek());
    }
    finally{
      builder.endUpdateSelection();
      selectionState.setInsideChange(false);
    }
  }

  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
    if(editor == null){
      presentation.setEnabled(false);
      return;
    }

    final Stack<ComponentPtr[]> history = editor.getSelectionState().getSelectionHistory();
    presentation.setEnabled(history.size() > 1);
  }
}
