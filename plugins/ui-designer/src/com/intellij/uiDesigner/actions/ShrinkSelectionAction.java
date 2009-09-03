package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.uiDesigner.SelectionState;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.propertyInspector.UIDesignerToolWindowManager;
import com.intellij.uiDesigner.componentTree.ComponentPtr;
import com.intellij.uiDesigner.componentTree.ComponentTreeBuilder;
import com.intellij.uiDesigner.designSurface.GuiEditor;

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
    ComponentTreeBuilder builder = UIDesignerToolWindowManager.getInstance(editor.getProject()).getComponentTreeBuilder();
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
