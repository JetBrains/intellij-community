package com.intellij.openapi.command.impl;

import com.intellij.openapi.fileEditor.FileEditor;

/**
 * author: lesya
 */
class Undo extends UndoOrRedo{
  public Undo(UndoManagerImpl manager, FileEditor editor) {
    super(manager, editor);
  }

  protected UndoRedoStacksHolder getStackHolder() {
    return myManager.getUndoStacksHolder();
  }

  protected UndoRedoStacksHolder getReverseStackHolder() {
    return myManager.getRedoStacksHolder();
  }

  protected String getActionName() {
    return "Undo";
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
