package com.intellij.openapi.command.impl;

import com.intellij.CommonBundle;
import com.intellij.openapi.fileEditor.FileEditor;

/**
 * author: lesya
 */
class Undo extends UndoOrRedo{
  public Undo(UndoManagerImpl manager, FileEditor editor) throws NothingToUndoException {
    super(manager, editor);
  }

  protected UndoRedoStacksHolder getStackHolder() {
    return myManager.getUndoStacksHolder();
  }

  protected UndoRedoStacksHolder getReverseStackHolder() {
    return myManager.getRedoStacksHolder();
  }

  protected String getActionName() {
    return CommonBundle.message("undo.dialog.title");
  }

  protected String getActionName(String commandName) {
    return CommonBundle.message("undo.command.confirmation.text", commandName);
  }

  protected void performAction() {
    myUndoableGroup.undo();
  }

  protected EditorAndState getBeforeState() {
    return myUndoableGroup.getStateAfter();
  }

  protected EditorAndState getAfterState() {
    return myUndoableGroup.getStateBefore();
  }

  protected void setBeforeState(EditorAndState state) {
    myUndoableGroup.setStateAfter(state);
  }
}
