
package com.intellij.openapi.command.undo;



public interface UndoableAction {
  /**
   * Undoes this action.
   * This method should not be invoked when any of its affected documents
   * were changed.
   * @see #getAffectedDocuments()
   */
  void undo() throws UnexpectedUndoException;
  void redo() throws UnexpectedUndoException;

  /**
   * Returns array of documents that are "affected" by this action.
   * If the returned value is null, all documents are "affected".
   * The action can be undone iff all of its affected documents are either
   * not affected by any of further actions or all of such actions are undone.
   */
  DocumentReference[] getAffectedDocuments();

  /**
   * Returns true if undoing of any command containing this action requires a confirmation dialog.
   */
  boolean isComplex();
}