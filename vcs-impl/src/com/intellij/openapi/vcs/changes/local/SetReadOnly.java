package com.intellij.openapi.vcs.changes.local;

import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListWorker;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.util.EventDispatcher;

public class SetReadOnly implements ChangeListCommand {
  private final String myName;
  private final boolean myValue;
  private boolean myResult;
  private LocalChangeList myListCopy;

  public SetReadOnly(final String name, final boolean value) {
    myName = name;
    myValue = value;
  }

  public void apply(final ChangeListWorker worker) {
    myResult = worker.setReadOnly(myName, myValue);
    myListCopy = worker.getCopyByName(myName);
  }

  public void doNotify(final EventDispatcher<ChangeListListener> dispatcher) {
    // +-
    dispatcher.getMulticaster().changeListChanged(myListCopy);
  }

  public void consume(final ChangeListWorker worker) {
    myResult = worker.setReadOnly(myName, myValue);
  }

  public boolean isResult() {
    return myResult;
  }
}
