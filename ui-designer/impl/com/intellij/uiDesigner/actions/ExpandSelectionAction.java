package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.componentTree.ComponentPtr;

import java.util.Stack;

/**
 * For each component selects all non selected siblings (if any). If
 * all component's siblings are already selected then selects component's
 * parent (if any).
 * 
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ExpandSelectionAction extends AnAction{
  /** Invoked by reflection */
  public ExpandSelectionAction() {}

  public void actionPerformed(final AnActionEvent e) {
    final GuiEditor editor = GuiEditorUtil.getEditorFromContext(e.getDataContext());
    final SelectionState selectionState = editor.getSelectionState();
    selectionState.setInsideChange(true);

    final Stack<ComponentPtr[]> history = selectionState.getSelectionHistory();

    try{
      final ComponentPtr[] ptrs = history.peek();
      for(int i = ptrs.length - 1; i >= 0; i--){
        // Skip invalid components
        final ComponentPtr ptr = ptrs[i];
        ptr.validate();
        if(!ptr.isValid()){
          continue;
        }

        // Extend selection
        final RadComponent component = ptr.getComponent();
        final RadContainer parent = component.getParent();
        if(parent == null){ // skip components without parents
          continue;
        }
        boolean shouldSelectParent = true;
        for(int j = parent.getComponentCount() - 1; j >= 0; j--){
          final RadComponent sibling = parent.getComponent(j);
          if(!sibling.isSelected()){
            shouldSelectParent = false;
            sibling.setSelected(true);
          }
        }
        if(shouldSelectParent){
          parent.setSelected(true);
        }
      }

      // Store new selection
      history.push(SelectionState.getPtrs(editor));
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

    final SelectionState selectionState = editor.getSelectionState();
    selectionState.setInsideChange(true);
    final Stack<ComponentPtr[]> history = selectionState.getSelectionHistory();

    presentation.setEnabled(!history.isEmpty());
  }
}
