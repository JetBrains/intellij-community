package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.SelectionState;
import com.intellij.uiDesigner.GuiEditor;
import com.intellij.uiDesigner.GuiEditorUtil;
import com.intellij.uiDesigner.componentTree.ComponentPtr;

import java.util.Stack;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ShrinkSelectionAction extends AnAction{
  public void actionPerformed(final AnActionEvent e) {
    final GuiEditor editor = GuiEditorUtil.getEditorFromContext(e.getDataContext());
    final SelectionState selectionState = editor.getSelectionState();
    selectionState.setInsideChange(true);

    try{
      final Stack<ComponentPtr[]> history = selectionState.getSelectionHistory();
      history.pop();
      FormEditingUtil.clearSelection(editor.getRootContainer());
      final ComponentPtr[] ptrs = history.peek();
      for(int i = ptrs.length - 1; i >= 0; i--){
        final ComponentPtr ptr = ptrs[i];
        ptr.validate();
        if(ptr.isValid()){
          ptr.getComponent().setSelected(true);
        }
      }
    }finally{
      selectionState.setInsideChange(false);
    }
  }

  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final GuiEditor editor = GuiEditorUtil.getEditorFromContext(e.getDataContext());
    if(editor == null){
      presentation.setEnabled(false);
      return;
    }

    final Stack<ComponentPtr[]> history = editor.getSelectionState().getSelectionHistory();
    presentation.setEnabled(history.size() > 1);
  }
}
