package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.diff.FragmentContent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;

import java.util.*;

/**
 * author: lesya
 */

class CommandMerger {
  private final UndoManagerImpl myManager;
  private Object myLastGroupId = null;
  private boolean myIsComplex = false;
  private boolean myOnlyUndoTransparents = true;
  private boolean myHasUndoTransparents = false;
  private String myCommandName = null;
  private ArrayList<UndoableAction> myCurrentActions = new ArrayList<UndoableAction>();
  private Set<DocumentReference> myAffectedDocuments = new HashSet<DocumentReference>();
  private DocumentAdapter myDocumentListener;
  private EditorAndState myStateBefore;
  private EditorAndState myStateAfter;
  private UndoConfirmationPolicy myUndoConfirmationPolicy = UndoConfirmationPolicy.DEFAULT;

  public CommandMerger(UndoManagerImpl manager, EditorFactory editorFactory) {
    myManager = manager;
    EditorEventMulticaster eventMulticaster = editorFactory.getEventMulticaster();
    myDocumentListener = new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        Document document = e.getDocument();
        if (myManager.isActive() && !myManager.isUndoInProgress() && !myManager.isRedoInProgress()) {
          myManager.getRedoStacksHolder().getStack(document).clear();
        }
      }
    };
    eventMulticaster.addDocumentListener(myDocumentListener);
  }

  public void dispose() {
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
    eventMulticaster.removeDocumentListener(myDocumentListener);
  }

  public void add(UndoableAction action, boolean isUndoTransparent) {
    if (!isUndoTransparent) myOnlyUndoTransparents = false;
    if (isUndoTransparent) myHasUndoTransparents = true;
    myCurrentActions.add(action);
    myAffectedDocuments.addAll(Arrays.asList(action.getAffectedDocuments()));
    myIsComplex |= action.isComplex() || affectsMultiplePhysicalDocs();
  }

  private boolean affectsMultiplePhysicalDocs() {
    if (myAffectedDocuments.size() < 2) return false;
    int count = 0;
    for (DocumentReference docRef : myAffectedDocuments) {
      VirtualFile file = docRef.getFile();
      if (file instanceof LightVirtualFile) continue;

      Document doc = docRef.getDocument();
      if (doc != null && doc.getUserData(FragmentContent.FRAGMENT_COPY) == Boolean.TRUE) continue;
      count++;
    }

    return count > 1;
  }

  public void commandFinished(String commandName, Object groupId, CommandMerger nextCommandToMerge) {
    if (!shouldMerge(groupId, nextCommandToMerge)) {
      flushCurrentCommand();
      myManager.compact();
    }
    merge(nextCommandToMerge);

    myLastGroupId = groupId;
    if (myCommandName == null) myCommandName = commandName;
  }

  private boolean shouldMerge(Object groupId, CommandMerger nextCommandToMerge) {
    if (myOnlyUndoTransparents && myHasUndoTransparents ||
        nextCommandToMerge.myOnlyUndoTransparents && nextCommandToMerge.myHasUndoTransparents) {
      return true;
    }
    if (myIsComplex || nextCommandToMerge.isComplex()) return false;

    return (groupId != null && Comparing.equal(myLastGroupId, groupId));
  }

  boolean isComplex() {
    return myIsComplex;
  }

  private void merge(CommandMerger nextCommandToMerge) {
    setBeforeState(nextCommandToMerge.myStateBefore);
    myCurrentActions.addAll(nextCommandToMerge.myCurrentActions);
    myAffectedDocuments.addAll(nextCommandToMerge.myAffectedDocuments);
    myIsComplex |= nextCommandToMerge.myIsComplex;
    myOnlyUndoTransparents &= nextCommandToMerge.myOnlyUndoTransparents;
    myHasUndoTransparents |= nextCommandToMerge.myHasUndoTransparents;
    myStateAfter = nextCommandToMerge.myStateAfter;
    mergeUndoConfirmationPolicy(nextCommandToMerge.getUndoConfirmationPolicy());
  }

  public UndoConfirmationPolicy getUndoConfirmationPolicy() {
    return myUndoConfirmationPolicy;
  }

  public void flushCurrentCommand() {
    if (!isEmpty()) {
      int commandCounter = myManager.getCommandCounterAndInc();
      UndoableGroup undoableGroup = new UndoableGroup(myCommandName, myIsComplex, myManager.getProject(),
                                                      myStateBefore, myStateAfter, commandCounter, myUndoConfirmationPolicy);
      undoableGroup.addTailActions(myCurrentActions);
      addToAllStacks(undoableGroup);
    }

    reset();
  }

  private void addToAllStacks(UndoableGroup commandInfo) {
    for (DocumentReference document : myAffectedDocuments) {
      myManager.getUndoStacksHolder().addToLocalStack(document, commandInfo);
    }

    if (myIsComplex) {
      myManager.getUndoStacksHolder().addToGlobalStack(commandInfo);
    }
  }

  public void undoOrRedo(FileEditor editor, boolean isUndo) {
    flushCurrentCommand();
    UndoOrRedo.execute(myManager, editor, isUndo);
  }

  public boolean isEmpty() {
    return myCurrentActions.isEmpty();
  }

  public boolean isEmpty(DocumentReference doc) {
    for (UndoableAction action : myCurrentActions) {
      for (DocumentReference document : action.getAffectedDocuments()) {
        if (document.equals(doc)) return false;
      }
    }

    return true;
  }

  public void reset() {
    myCurrentActions = new ArrayList<UndoableAction>();
    myAffectedDocuments = new HashSet<DocumentReference>();
    myLastGroupId = null;
    myIsComplex = false;
    myOnlyUndoTransparents = true;
    myHasUndoTransparents = false;
    myCommandName = null;
    myStateAfter = null;
    myStateBefore = null;
    myUndoConfirmationPolicy = UndoConfirmationPolicy.DEFAULT;
  }

  public void setBeforeState(EditorAndState state) {
    if (myStateBefore == null || isEmpty()) {
      myStateBefore = state;
    }
  }

  public void setAfterState(EditorAndState state) {
    myStateAfter = state;
  }

  public Collection<DocumentReference> getAffectedDocuments() {
    return myAffectedDocuments;
  }

  public void mergeUndoConfirmationPolicy(UndoConfirmationPolicy undoConfirmationPolicy){
    if (myUndoConfirmationPolicy == UndoConfirmationPolicy.DEFAULT){
      myUndoConfirmationPolicy = undoConfirmationPolicy;
    } else if (myUndoConfirmationPolicy == UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION){
        if (undoConfirmationPolicy == UndoConfirmationPolicy.REQUEST_CONFIRMATION){
          myUndoConfirmationPolicy = UndoConfirmationPolicy.REQUEST_CONFIRMATION;
        }
    }
  }

}
