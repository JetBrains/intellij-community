package com.intellij.openapi.command.impl;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceByDocument;
import com.intellij.openapi.command.undo.NonUndoableAction;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.util.*;

/**
 * author: lesya
 */
abstract class UndoOrRedo {
  protected final UndoManagerImpl myManager;
  private final FileEditor myEditor;
  protected final UndoableGroup myUndoableGroup;

  public UndoOrRedo(UndoManagerImpl manager, FileEditor editor) {
    myManager = manager;
    myEditor = editor;
    myUndoableGroup = getStack().getLast();
  }

  protected abstract UndoRedoStacksHolder getStackHolder();

  protected abstract UndoRedoStacksHolder getReverseStackHolder();

  protected abstract String getActionName();

  protected abstract EditorAndState getBeforeState();

  protected abstract EditorAndState getAfterState();

  protected abstract void performAction();

  protected abstract void setBeforeState(EditorAndState state);

  private Collection<Document> collectReadOnlyDocuments() {
    Collection<DocumentReference> affectedDocument = myUndoableGroup.getAffectedDocuments();
    Collection<Document> readOnlyDocs = new ArrayList<Document>();
    for (DocumentReference ref : affectedDocument) {
      if (ref instanceof DocumentReferenceByDocument) {
        Document doc = ref.getDocument();
        if (doc != null && !doc.isWritable()) readOnlyDocs.add(doc);
      }
    }
    return readOnlyDocs;
  }

  private Collection<VirtualFile> collectReadOnlyAffectedFiles() {
    Collection<DocumentReference> affectedDocument = myUndoableGroup.getAffectedDocuments();
    Collection<VirtualFile> readOnlyFiles = new ArrayList<VirtualFile>();
    for (DocumentReference documentReference : affectedDocument) {
      VirtualFile file = documentReference.getFile();
      if ((file != null) && file.isValid() && !file.isWritable()) {
        readOnlyFiles.add(file);
      }
    }
    return readOnlyFiles;
  }

  public void execute() {
    Set<DocumentReference> otherAffected = new HashSet<DocumentReference>();
    if (containsAnotherChanges(otherAffected)) {
      //otherAffected.removeAll(myUndoableGroup.getAffectedDocuments());
      reportCannotUndo(CommonBundle.message("cannot.undo.error.other.affected.files.changed.message"), otherAffected);
      return;
    }

    if (containsNonUndoableActions()) {
      reportCannotUndo(CommonBundle.message("cannot.undo.error.contains.nonundoable.changes.message"), myUndoableGroup.getAffectedDocuments());
      return;
    }

    if (myUndoableGroup.askConfirmation()) {
      if (canceledByUser()) {
        return;
      }
    }
    else {
      if (restore(getBeforeState())) {
        setBeforeState(new EditorAndState(myEditor, myEditor.getState(FileEditorStateLevel.UNDO)));
        return;
      }
    }

    Collection<VirtualFile> readOnlyFiles = collectReadOnlyAffectedFiles();
    if (!readOnlyFiles.isEmpty()) {
      final Project project = myManager.getProject();
      final VirtualFile[] files = readOnlyFiles.toArray(new VirtualFile[readOnlyFiles.size()]);

      if (project == null) {
        VirtualFileManager.getInstance().fireReadOnlyModificationAttempt(files);
        return;
      }

      final ReadonlyStatusHandler.OperationStatus operationStatus = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(files);
      if (operationStatus.hasReadonlyFiles()) return;
    }

    Collection<Document> readOnlyDocuments = collectReadOnlyDocuments();
    if (!readOnlyDocuments.isEmpty()) {
      for (Document document : readOnlyDocuments) {
        document.fireReadOnlyModificationAttempt();
      }
      return;
    }

    removeLastFromMyStacks();
    addLastToReverseStacks();

    performAction();

    restore(getAfterState());
  }

  private boolean containsNonUndoableActions() {
    final UndoableAction[] actions = myUndoableGroup.getActions();
    for (UndoableAction action : actions) {
      if (action instanceof NonUndoableAction) return true;
    }
    return false;
  }

  private boolean restore(EditorAndState pair) {
    if (myEditor == null || pair == null || pair.getEditor() == null) {
      return false;
    }

    // we cannot simply compare editors here because of the following scenario:
    // 1. make changes in editor for file A
    // 2. move caret
    // 3. close editor
    // 4. re-open editor for A via Ctrl-E
    // 5. undo -> position is not affected, because instance created in step 4 is not the same!!! 
    if (!myEditor.getClass().equals(pair.getEditor().getClass())) {
      return false;
    }

    // If current editor state isn't equals to remembered state then
    // we have to try to restore previous state. But sometime it's
    // not possible to restore it. For example, it's not possible to
    // restore scroll proportion if editor doesn not have scrolling any more.
    FileEditorState currentState = myEditor.getState(FileEditorStateLevel.UNDO);
    if (currentState.equals(pair.getState())) {
      return false;
    }

    myEditor.setState(pair.getState());
    return true;
  }

  private boolean canceledByUser() {
    String actionText = getActionName(myUndoableGroup.getCommandName());

    if (actionText.length() > 80) {
      actionText = actionText.substring(0, 80) + "... ";
    }

    return Messages.showOkCancelDialog(myManager.getProject(), actionText + "?", getActionName(),
                                       Messages.getQuestionIcon()) != DialogWrapper.OK_EXIT_CODE;
  }

    protected abstract String getActionName(String commandName);

    private void addLastToReverseStacks() {
      Collection<LinkedList<UndoableGroup>> stacks = getStacks(getReverseStackHolder());
      for (LinkedList<UndoableGroup> linkedList : stacks) {
        linkedList.addLast(myUndoableGroup);
      }
      if (myUndoableGroup.isComplex()) {
        getReverseStackHolder().getGlobalStack().addLast(myUndoableGroup);
      }
    }

  private Collection<DocumentReference> getDocumentsReferences() {
    return myUndoableGroup.getAffectedDocuments();
  }

  private void removeLastFromMyStacks() {
    for (LinkedList<UndoableGroup> linkedList : getStacks()) {
      linkedList.removeLast();
    }
    if (myUndoableGroup.isComplex()) {
      getStackHolder().getGlobalStack().removeLast();
    }
  }

  private void reportCannotUndo(final String message, final Collection<DocumentReference> problemFiles) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException("Files changed");
    }

    new CannotUndoReportDialog(myManager.getProject(), message, problemFiles).show();

    //Messages.showMessageDialog(myManager.getProject(), message, title, Messages.getErrorIcon());
  }

  private boolean containsAnotherChanges(Set<DocumentReference> affected) {
    boolean otherDocsHaveSomethingOnTheirStacks = false;
    for (LinkedList<UndoableGroup> linkedList : getStacks()) {
      if (linkedList.isEmpty()) continue;
      final UndoableGroup last = linkedList.getLast();
      if (!last.equals(myUndoableGroup)) {
        otherDocsHaveSomethingOnTheirStacks = true;
        affected.addAll(last.getAffectedDocuments());
      }
    }

    if (otherDocsHaveSomethingOnTheirStacks) return true;

    if (myUndoableGroup.isComplex()) {
      final UndoableGroup lastGlobal = getStackHolder().getGlobalStack().getLast();
      if (!lastGlobal.equals(myUndoableGroup)) {
        affected.addAll(lastGlobal.getAffectedDocuments());
        return true;
      }
    }

    return false;
  }

  private Collection<LinkedList<UndoableGroup>> getStacks() {
    return getStacks(getStackHolder());
  }

  private Collection<LinkedList<UndoableGroup>> getStacks(UndoRedoStacksHolder stackHolder) {
    ArrayList<LinkedList<UndoableGroup>> result = new ArrayList<LinkedList<UndoableGroup>>();
    for (final DocumentReference documentReference : getDocumentsReferences()) {
      result.add(stackHolder.getStack(documentReference));
    }
    return result;
  }


  private LinkedList<UndoableGroup> getStack() {
    if (myEditor == null) {
      return getStackHolder().getGlobalStack();
    }
    else {
      long recentDocumentTimeStamp = -1;
      LinkedList<UndoableGroup> result = null;
      Document[] documents = TextEditorProvider.getDocuments(myEditor);
      for (Document document : documents) {
        LinkedList<UndoableGroup> stack = getStackHolder().getStack(document);
        if (!stack.isEmpty()) {
          long modificationStamp = document.getModificationStamp();
          if (recentDocumentTimeStamp < modificationStamp) {
            result = stack;
            recentDocumentTimeStamp = modificationStamp;
          }
        }
      }
      if (result != null) {
        return result;
      }
      else {
        throw new RuntimeException("Cannot find stack");
      }
    }
  }

  public static void execute(UndoManagerImpl manager, FileEditor editor, boolean isUndo) {
    if (isUndo) {
      new Undo(manager, editor).execute();
    }
    else {
      new Redo(manager, editor).execute();
    }
  }

}


