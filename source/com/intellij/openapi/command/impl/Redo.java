package com.intellij.openapi.command.impl;

import com.intellij.openapi.fileEditor.FileEditor;

/**
 * author: lesya
 */
class Redo extends UndoOrRedo{
  public Redo(UndoManagerImpl manager, FileEditor editor) {
    super(manager, editor);
  }

  protected UndoRedoStacksHolder getStackHolder() {
    return myManager.getRedoStacksHolder();
  }

  protected UndoRedoStacksHolder getReverseStackHolder() {
    return myManager.getUndoStacksHolder();
  }

  protected String getActionName() {
    return "Redo";
  }

  protected void performAction() {
    myUndoableGroup.redo();
  }

  protected EditorAndState getBeforeState() {
    return myUndoableGroup.getStateBefore();
  }

  protected EditorAndState getAfterState() {
    return myUndoableGroup.getStateAfter();
  }

  protected void setBeforeState(EditorAndState state) {
    myUndoableGroup.setStateBefore(state);
  }
}
