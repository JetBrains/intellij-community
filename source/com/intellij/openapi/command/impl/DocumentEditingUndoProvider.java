package com.intellij.openapi.command.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.NonUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExternalChangeAction;

/**
 * author: lesya
 */
class DocumentEditingUndoProvider {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.openapi.command.impl.DocumentEditingUndoProvider");

  private MyEditorDocumentListener myDocumentListener;
  private Project myProject;

  public DocumentEditingUndoProvider(Project project, EditorFactory editorFactory) {
    myDocumentListener = new MyEditorDocumentListener();
    myProject = project;

    EditorEventMulticaster eventMulticaster = editorFactory.getEventMulticaster();
    eventMulticaster.addDocumentListener(myDocumentListener);
  }

  public void dispose() {
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
    eventMulticaster.removeDocumentListener(myDocumentListener);
  }

  private class MyEditorDocumentListener extends DocumentAdapter {
    public void documentChanged(final DocumentEvent e) {
      final Document document = e.getDocument();
      if (allEditorsAreViewersFor(document)) return;
      UndoManagerImpl undoManager = getUndoManager();
      if (externalChanges()) {
        createNonUndoableAction(document);
      }
      else {
        LOG.assertTrue(
          undoManager.isInsideCommand(),
          "Undoable actions allowed inside commands only (see com.intellij.openapi.command.CommandProcessor.executeCommand())"
        );
        if (undoManager.isActive()) {
          createUndoableEditorChangeAction(e);
        }
        else {
          createNonUndoableAction(document);
        }
      }
    }

    private boolean allEditorsAreViewersFor(Document document) {
      Editor[] editors = EditorFactory.getInstance().getEditors(document);
      if (editors.length == 0) return false;
      for (int i = 0; i < editors.length; i++) {
        Editor editor = editors[i];
        if (!editor.isViewer()) return false;
      }
      return true;
    }

    private void createUndoableEditorChangeAction(final DocumentEvent e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("document changed:");
        LOG.debug("  offset:" + e.getOffset());
        LOG.debug("  old fragment:" + e.getOldFragment());
        LOG.debug("  new fragment:" + e.getNewFragment());
      }

      EditorChangeAction action = new EditorChangeAction((DocumentEx)e.getDocument(), e.getOffset(),
                                                         e.getOldFragment(), e.getNewFragment(), e.getOldTimeStamp(), myProject);

      getUndoManager().undoableActionPerformed(action);
    }

    private void createNonUndoableAction(final Document document) {
      UndoManagerImpl undoManager = getUndoManager();
      if (undoManager.undoableActionsForDocumentAreEmpty(DocumentReferenceByDocument.createDocumentReference(document))) {
        return;
      }
      undoManager.undoableActionPerformed(
        new NonUndoableAction() {
          public DocumentReference[] getAffectedDocuments() {
            return new DocumentReference[]{DocumentReferenceByDocument.createDocumentReference(document)};
          }

          public boolean isComplex() {
            return false;
          }
        }
      );
    }

    private boolean externalChanges() {
      return ApplicationManager.getApplication().getCurrentWriteAction(PsiExternalChangeAction.class) != null;
    }
  }

  private UndoManagerImpl getUndoManager() {
    return (UndoManagerImpl)(myProject == null ? UndoManager.getGlobalInstance() : UndoManager.getInstance(myProject));
  }

  static class EditorPosition {
    int line;
    int col;
    final float scrollProportion;

    public EditorPosition(int line, int col, float scrollProportion) {
      this.line = line;
      this.col = col;
      this.scrollProportion = scrollProportion;
    }

    public boolean equals(Object obj) {
      if (obj instanceof EditorPosition) {
        EditorPosition position = (EditorPosition)obj;
        return position.line == line && position.col == col;
      }
      return false;
    }

    public int hashCode() {
      return line + col;
    }
  }
}
