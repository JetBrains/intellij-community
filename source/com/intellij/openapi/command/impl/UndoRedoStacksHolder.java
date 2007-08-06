package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceByDocument;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;

/**
 * author: lesya
 */

class UndoRedoStacksHolder {
  private final Key<LinkedList<UndoableGroup>> STACK_IN_DOCUMENT_KEY = Key.create("STACK_IN_DOCUMENT_KEY");

  private UndoManagerImpl myManager;

  private LinkedList<UndoableGroup> myGlobalStack = new LinkedList<UndoableGroup>();
  private HashMap<DocumentReference, LinkedList<UndoableGroup>> myStackOwnerToStack = new HashMap<DocumentReference, LinkedList<UndoableGroup>>();

  public UndoRedoStacksHolder(UndoManagerImpl m) {
    myManager = m;
  }

  public LinkedList<UndoableGroup> getStack(Document d) {
    Document original = myManager.getOriginal(d);
    return getStack(DocumentReferenceByDocument.createDocumentReference(original));
  }

  public LinkedList<UndoableGroup> getStack(@NotNull DocumentReference docRef) {
    LinkedList<UndoableGroup> result;
    if (docRef.getFile() != null) {
      result = myStackOwnerToStack.get(docRef);
      if (result == null) {
        result = new LinkedList<UndoableGroup>();
        myStackOwnerToStack.put(docRef, result);
      }
    }
    else {
      result = docRef.getDocument().getUserData(STACK_IN_DOCUMENT_KEY);
      if (result == null) {
        result = new LinkedList<UndoableGroup>();
        docRef.getDocument().putUserData(STACK_IN_DOCUMENT_KEY, result);
      }
    }

    return result;
  }

  public void clearFileQueue(DocumentReference docRef) {
    final LinkedList<UndoableGroup> queue = getStack(docRef);
    clear(queue);
    if (docRef.getFile() != null) {
      myStackOwnerToStack.remove(docRef);
    }
    else {
      docRef.getDocument().putUserData(STACK_IN_DOCUMENT_KEY, null);
    }
  }

  private void clear(LinkedList<UndoableGroup> stack) {
    for (UndoableGroup undoableGroup : stack) {
      undoableGroup.dispose();
    }
    stack.clear();
  }

  public LinkedList<UndoableGroup> getGlobalStack() {
    return myGlobalStack;
  }

  public void clearGlobalStack() {
    myGlobalStack.clear();
  }

  public void addToLocalStack(DocumentReference docRef, UndoableGroup commandInfo) {
    addToStack(getStack(docRef), commandInfo, UndoManagerImpl.LOCAL_UNDO_LIMIT);
  }

  public void addToGlobalStack(UndoableGroup commandInfo) {
    addToStack(getGlobalStack(), commandInfo, UndoManagerImpl.GLOBAL_UNDO_LIMIT);
  }

  private void addToStack(LinkedList<UndoableGroup> stack, UndoableGroup commandInfo, int limit) {
    stack.addLast(commandInfo);
    while (stack.size() > limit) {
      UndoableGroup undoableGroup = stack.removeFirst();
      undoableGroup.dispose();
    }
  }

  public void clearEditorStack(FileEditor editor) {
    Document[] documents = TextEditorProvider.getDocuments(editor);
    for (Document document : documents) {
      clear(getStack(DocumentReferenceByDocument.createDocumentReference(document)));
    }

  }

  public Set<DocumentReference> getAffectedDocuments() {
    return myStackOwnerToStack.keySet();
  }

  public Set<DocumentReference> getDocsInGlobalQueue() {
    HashSet<DocumentReference> result = new HashSet<DocumentReference>();
    for (UndoableGroup group : getGlobalStack()) {
      result.addAll(group.getAffectedDocuments());
    }
    return result;
  }

  public int getYoungestCommandAge(DocumentReference docRef) {
    final LinkedList<UndoableGroup> stack = getStack(docRef);
    if (stack.isEmpty()) return 0;
    return Math.max(stack.getFirst().getCommandCounter(), stack.getLast().getCommandCounter());
  }

  public Collection<DocumentReference> getGlobalStackAffectedDocuments() {
    Collection<DocumentReference> result = new HashSet<DocumentReference>();
    for (UndoableGroup undoableGroup : myGlobalStack) {
      result.addAll(undoableGroup.getAffectedDocuments());
    }
    return result;
  }

  public void dropHistory() {
    clearGlobalStack();
    myStackOwnerToStack.clear();
  }
}
