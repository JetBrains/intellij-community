
package com.intellij.openapi.command.undo;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class UndoManager {
  public static UndoManager getInstance(Project project) {
    return project.getComponent(UndoManager.class);
  }

  public static UndoManager getGlobalInstance() {
    return ApplicationManager.getApplication().getComponent(UndoManager.class);
  }

  public abstract void undoableActionPerformed(UndoableAction action);

  public abstract boolean isUndoInProgress();
  public abstract boolean isRedoInProgress();

  public abstract void undo(FileEditor editor);
  public abstract void redo(FileEditor editor);
  public abstract boolean isUndoAvailable(FileEditor editor);
  public abstract boolean isRedoAvailable(FileEditor editor);

  public abstract void clearUndoRedoQueue(VirtualFile file);
  public abstract void clearUndoRedoQueue(FileEditor editor);
  public abstract void clearUndoRedoQueue(Document document);

  public abstract void dropHistory();
}