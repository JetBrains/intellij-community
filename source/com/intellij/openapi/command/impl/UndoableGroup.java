package com.intellij.openapi.command.impl;

import com.intellij.openapi.Disposeable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.localVcs.LocalVcs;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import java.util.*;

/**
 * @author max
 */
class UndoableGroup {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.command.impl.UndoableGroup");

  private String myCommandName;
  private boolean myComplex;
  private int myCommandCounter;
  private ArrayList<UndoableAction> myActions;
  private EditorAndState myStateBefore;
  private EditorAndState myStateAfter;
  private Project myProject;
  private final UndoConfirmationPolicy myUndoConfirmationPolicy;

  public UndoableGroup(String commandName, boolean isComplex, Project project, EditorAndState stateBefore, EditorAndState stateAfter, int commandCounter,
                       UndoConfirmationPolicy undoConfirmationPolicy) {
    myCommandName = commandName;
    myComplex = isComplex;
    myCommandCounter = commandCounter;
    myActions = new ArrayList<UndoableAction>();
    myProject = project;
    myStateBefore = stateBefore;
    myStateAfter = stateAfter;
    myUndoConfirmationPolicy = undoConfirmationPolicy;
  }

  public boolean isComplex() {
    return myComplex;
  }

  public UndoableAction[] getActions() {
    return myActions.toArray(new UndoableAction[myActions.size()]);
  }

  public void addTailActions(Collection<UndoableAction> actions) {
    myActions.addAll(actions);
  }

  private Iterator<UndoableAction> reverseIterator(final ListIterator<UndoableAction> iter) {
    return new Iterator<UndoableAction>() {
      public boolean hasNext() {
        return iter.hasPrevious();
      }

      public UndoableAction next() {
        return iter.previous();
      }

      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  private void undoOrRedo(final boolean isUndo) {
    LvcsAction actionStarted = LvcsAction.EMPTY;
    if (myProject != null) {
      LocalVcs lvcs = LocalVcs.getInstance(myProject);
      if (isComplex() && lvcs != null) {
        actionStarted = lvcs.startAction(actionName(isUndo) + " " + myCommandName, "", false);
      }
    }
    try {
      performWritableUndoOrRedoAction(isUndo);
    } finally {
      actionStarted.finish();
    }
  }

  private void performWritableUndoOrRedoAction(final boolean isUndo) {
    final Iterator<UndoableAction> each = isUndo ? reverseIterator(myActions.listIterator(myActions.size())) : myActions.iterator();

    ApplicationManager.getApplication().runWriteAction(
        new Runnable() {
          public void run() {
            String message = "Cannot " + actionName(isUndo);

            while (each.hasNext()) {
              UndoableAction undoableAction = each.next();
              try {
                if (isUndo)
                  undoableAction.undo();
                else
                  undoableAction.redo();
              } catch (UnexpectedUndoException e) {
                if (!ApplicationManager.getApplication().isUnitTestMode()) {
                  if (e.getMessage() != null) {
                    message += ".\n" + e.getMessage();
                  }
                  Messages.showMessageDialog(myProject, message, message, Messages.getErrorIcon());
                } else {
                  LOG.error(e);
                }
              }
            }
          }
        }
    );
  }

  private String actionName(final boolean isUndo) {
    return isUndo ? "Undo" : "Redo";
  }

  public void undo() {
    undoOrRedo(true);
  }

  public void redo() {
    undoOrRedo(false);
  }

  public Collection<DocumentReference> getAffectedDocuments() {
    Set<DocumentReference> result = new HashSet<DocumentReference>();
    for (Iterator iterator = myActions.iterator(); iterator.hasNext();) {
      UndoableAction action = (UndoableAction) iterator.next();
      result.addAll(new ArrayList(Arrays.asList(action.getAffectedDocuments())));
    }
    return result;
  }

  public EditorAndState getStateBefore() {
    return myStateBefore;
  }

  public EditorAndState getStateAfter() {
    return myStateAfter;
  }

  public void setStateBefore(EditorAndState stateBefore) {
    myStateBefore = stateBefore;
  }

  public void setStateAfter(EditorAndState stateAfter) {
    myStateAfter = stateAfter;
  }

  public void dispose() {
    for (Iterator each = myActions.iterator(); each.hasNext();) {
      UndoableAction undoableAction = (UndoableAction) each.next();
      if (undoableAction instanceof Disposeable) ((Disposeable) undoableAction).dispose();

    }
  }

  public String getCommandName() {
    return myCommandName;
  }

  public int getCommandCounter() {
    return myCommandCounter;
  }

  public boolean askConfirmation() {
    if (myUndoConfirmationPolicy == UndoConfirmationPolicy.REQUEST_CONFIRMATION){
      return true;
    } else if (myUndoConfirmationPolicy == UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION){
      return false;
    } else {
      return isComplex() || (getAffectedDocuments().size() > 1);
    }
  }
}
