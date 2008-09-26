package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.changes.local.*;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

/** synchronization aspect is external for this class; only logic here
 * have internal command queue; applies commands to another copy of change lists (ChangeListWorker) and sends notifications
 * (after update is done)
 */
public class Modifier implements ChangeListsWriteOperations {
  private final ChangeListWorker myWorker;
  private boolean myInsideUpdate;
  private final List<ChangeListCommand> myCommandQueue;
  private final EventDispatcher<ChangeListListener> myDispatcher;

  public Modifier(final ChangeListWorker worker, final EventDispatcher<ChangeListListener> dispatcher) {
    myWorker = worker;
    myCommandQueue = new LinkedList<ChangeListCommand>();
    myDispatcher = dispatcher;
  }

  public LocalChangeList addChangeList(@NotNull final String name, final String comment) {
    final AddList command = new AddList(name, comment, myInsideUpdate);
    impl(command);
    return command.getNewListCopy();
  }

  public String setDefault(final String name) {
    final SetDefault command = new SetDefault(name);
    impl(command);
    return command.getPrevious();
  }

  public boolean removeChangeList(@NotNull final String name) {
    final RemoveList command = new RemoveList(name);
    impl(command);
    return command.isRemoved();
  }

  public MultiMap<LocalChangeList, Change> moveChangesTo(final String name, final Change[] changes) {
    final MoveChanges command = new MoveChanges(name, changes);
    impl(command);
    return command.getMovedFrom();
  }

  private void impl(final ChangeListCommand command) {
    command.apply(myWorker);
    if (myInsideUpdate) {
      myCommandQueue.add(command);
      // notify after change lsist are synchronized
    } else {
      // notify immediately
      command.doNotify(myDispatcher);
    }
  }

  public boolean setReadOnly(final String name, final boolean value) {
    final SetReadOnly command = new SetReadOnly(name, value);
    impl(command);
    return command.isResult();
  }

  public boolean editName(@NotNull final String fromName, @NotNull final String toName) {
    final EditName command = new EditName(fromName, toName);
    impl(command);
    return command.isResult();
  }

  public String editComment(@NotNull final String fromName, final String newComment) {
    final EditComment command = new EditComment(fromName, newComment);
    impl(command);
    return command.getOldComment();
  }

  public boolean isInsideUpdate() {
    return myInsideUpdate;
  }

  public void enterUpdate() {
    myInsideUpdate = true;
  }

  public void exitUpdate() {
    myInsideUpdate = false;
  }

  public void clearQueue() {
    myCommandQueue.clear();
  }

  public void apply(final ChangeListWorker worker) {
    for (ChangeListCommand command : myCommandQueue) {
      command.apply(worker);
      command.doNotify(myDispatcher);
    }
  }
}
