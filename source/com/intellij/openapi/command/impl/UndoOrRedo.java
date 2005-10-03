package com.intellij.openapi.command.impl;

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
import com.intellij.CommonBundle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

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
    myUndoableGroup = (UndoableGroup)getStack().getLast();
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
    for (Iterator<DocumentReference> iterator = affectedDocument.iterator(); iterator.hasNext();) {
      DocumentReference ref = iterator.next();
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
    for (Iterator each = affectedDocument.iterator(); each.hasNext();) {
      DocumentReference documentReference = (DocumentReference)each.next();
      VirtualFile file = documentReference.getFile();
      if ((file != null) && file.isValid() && !file.isWritable()) {
        readOnlyFiles.add(file);
      }
    }
    return readOnlyFiles;
  }

  public void execute() {
    if (containsAnotherChanges() || containsNonUndoableActions()) {
      reportCannotUndo();
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
      for (Iterator<Document> iterator = readOnlyDocuments.iterator(); iterator.hasNext();) {
        Document document = iterator.next();
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
    for (int i = 0; i < actions.length; i++) {
      if (actions[i] instanceof NonUndoableAction) return true;
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
      Collection stacks = getStacks(getReverseStackHolder());
      for (Iterator i = stacks.iterator(); i.hasNext();) {
        LinkedList linkedList = (LinkedList)i.next();
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
    for (Iterator i = getStacks().iterator(); i.hasNext();) {
      LinkedList linkedList = (LinkedList)i.next();
      linkedList.removeLast();
    }
    if (myUndoableGroup.isComplex()) {
      getStackHolder().getGlobalStack().removeLast();
    }
  }

  private void reportCannotUndo() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException("Files changed");
    }

    Messages.showMessageDialog(myManager.getProject(), CommonBundle.message("cannot.undo.error.message"), CommonBundle.message("undo.dialog.title"),
                               Messages.getErrorIcon());
  }

  private boolean containsAnotherChanges() {
    for (Iterator<LinkedList> each = getStacks().iterator(); each.hasNext();) {
      LinkedList linkedList = each.next();
      if (linkedList.isEmpty()) continue;
      if (!linkedList.getLast().equals(myUndoableGroup)) return true;
    }
    return (myUndoableGroup.isComplex() && !getStackHolder().getGlobalStack().getLast().equals(myUndoableGroup));

  }

  private Collection<LinkedList> getStacks() {
    return getStacks(getStackHolder());
  }

  private Collection<LinkedList> getStacks(UndoRedoStacksHolder stackHolder) {
    ArrayList<LinkedList> result = new ArrayList<LinkedList>();
    for (Iterator<DocumentReference> i = getDocumentsReferences().iterator(); i.hasNext();) {
      result.add(stackHolder.getStack(i.next()));
    }
    return result;
  }


  private LinkedList getStack() {
    if (myEditor == null) {
      return getStackHolder().getGlobalStack();
    }
    else {
      long recentDocumentTimeStamp = -1;
      LinkedList<UndoableGroup> result = null;
      Document[] documents = TextEditorProvider.getDocuments(myEditor);
      for (int i = 0; i < documents.length; i++) {
        Document document = documents[i];
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


