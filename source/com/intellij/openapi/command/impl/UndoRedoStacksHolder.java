package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceByDocument;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;


class UndoRedoStacksHolder {
  private UndoManagerImpl myManager;

  private LinkedList<UndoableGroup> myGlobalStack = new LinkedList<UndoableGroup>();
  private Map<DocumentReference, LinkedList<UndoableGroup>> myDocRefStacks = new HashMap<DocumentReference, LinkedList<UndoableGroup>>();

  public UndoRedoStacksHolder(UndoManagerImpl m) {
    myManager = m;
  }

  public LinkedList<UndoableGroup> getStack(Document d) {
    Document original = myManager.getOriginal(d);
    return getStack(DocumentReferenceByDocument.createDocumentReference(original));
  }

  public LinkedList<UndoableGroup> getStack(@NotNull DocumentReference docRef) {
    LinkedList<UndoableGroup> result = myDocRefStacks.get(docRef);
    if (result == null) {
      result = new LinkedList<UndoableGroup>();
      myDocRefStacks.put(docRef, result);
    }
    return result;
  }

  public void clearFileStack(DocumentReference docRef) {
    LinkedList<UndoableGroup> stack = getStack(docRef);
    stack.clear();
    myDocRefStacks.remove(docRef);
  }

  public LinkedList<UndoableGroup> getGlobalStack() {
    return myGlobalStack;
  }

  public void clearGlobalStack() {
    myGlobalStack.clear();
  }

  public void clearStacksWithComplexCommands() {
    clearGlobalStack();
    for (LinkedList<UndoableGroup> stack : myDocRefStacks.values()) {
      clearStackUpToLastComplexCommand(stack);
    }
  }

  private void clearStackUpToLastComplexCommand(LinkedList<UndoableGroup> stack) {
    int removeUpTo = -1;

    for (int i = stack.size() - 1; i >= 0; i--) {
      if (stack.get(i).isComplex()) {
        removeUpTo = i;
        break;
      }
    }

    while (removeUpTo-- >= 0) {
      stack.removeFirst();
    }
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
      stack.removeFirst();
    }
  }

  public void clearEditorStack(FileEditor editor) {
    Document[] documents = TextEditorProvider.getDocuments(editor);
    for (Document document : documents) {
      DocumentReference ref = DocumentReferenceByDocument.createDocumentReference(document);
      LinkedList<UndoableGroup> stack = getStack(ref);
      stack.clear();
    }
  }

  public Set<DocumentReference> getAffectedDocuments() {
    return myDocRefStacks.keySet();
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
    myDocRefStacks.clear();
  }
}
