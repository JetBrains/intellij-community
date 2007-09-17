package com.intellij.openapi.command.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceByDocument;
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
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiExternalChangeAction;
import com.intellij.psi.PsiManager;

/**
 * author: lesya
 */
class DocumentEditingUndoProvider {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.openapi.command.impl.DocumentEditingUndoProvider");

  private final MyEditorDocumentListener myDocumentListener;
  private final Project myProject;

  public DocumentEditingUndoProvider(Project project, EditorFactory editorFactory) {
    myDocumentListener = new MyEditorDocumentListener();
    myProject = project;

    EditorEventMulticaster m = editorFactory.getEventMulticaster();
    m.addDocumentListener(myDocumentListener);
  }

  public void dispose() {
    EditorEventMulticaster m = EditorFactory.getInstance().getEventMulticaster();
    m.removeDocumentListener(myDocumentListener);
  }

  private boolean isCopy(Document d) {
    return getUndoManager().isCopy(d);
  }

  private UndoManagerImpl getUndoManager() {
    return (UndoManagerImpl)(myProject == null ? UndoManager.getGlobalInstance() : UndoManager.getInstance(myProject));
  }

  private class MyEditorDocumentListener extends DocumentAdapter {
    public void documentChanged(final DocumentEvent e) {
      final Document document = e.getDocument();
      if (isCopy(document)) return;
      if (allEditorsAreViewersFor(document)) return;
      if (myProject != null && !isToPostEvents(document)) return;

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

    private boolean isToPostEvents(final Document document) {
      final VirtualFile vFile = FileDocumentManager.getInstance().getFile(document);
      if (vFile != null) {
        final FileViewProvider viewProvider = PsiManager.getInstance(myProject).findViewProvider(vFile);
        if (viewProvider != null && !viewProvider.isEventSystemEnabled()) return false;
      }
      return true;
    }

    private boolean allEditorsAreViewersFor(Document document) {
      Editor[] editors = EditorFactory.getInstance().getEditors(document);
      if (editors.length == 0) return false;
      for (Editor editor : editors) {
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
                                                         e.getOldFragment(), e.getNewFragment(), e.getOldTimeStamp());

      getUndoManager().undoableActionPerformed(action);
    }

    private void createNonUndoableAction(final Document document) {
      UndoManagerImpl undoManager = getUndoManager();
      final DocumentReference ref = DocumentReferenceByDocument.createDocumentReference(document);
      if (undoManager.undoableActionsForDocumentAreEmpty(ref)) {
        return;
      }
      undoManager.undoableActionPerformed(
        new NonUndoableAction() {
          public DocumentReference[] getAffectedDocuments() {
            return new DocumentReference[]{ref};
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
}
