package com.intellij.openapi.vcs.changes.local;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListWorker;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.MultiMap;

import java.util.Collection;

public class MoveChanges implements ChangeListCommand {
  private final String myName;
  private final Change[] myChanges;
  private MultiMap<LocalChangeList,Change> myMovedFrom;
  private LocalChangeList myListCopy;

  public MoveChanges(final String name, final Change[] changes) {
    myName = name;
    myChanges = changes;
  }

  public void apply(final ChangeListWorker worker) {
    myMovedFrom = worker.moveChangesTo(myName, myChanges);
    myListCopy = worker.getCopyByName(myName);
  }

  public void doNotify(final EventDispatcher<ChangeListListener> dispatcher) {
    for(LocalChangeList fromList: myMovedFrom.keySet()) {
      final Collection<Change> changesInList = myMovedFrom.get(fromList);
      dispatcher.getMulticaster().changesMoved(changesInList, fromList, myListCopy);
    }
  }

  public MultiMap<LocalChangeList, Change> getMovedFrom() {
    return myMovedFrom;
  }
}
