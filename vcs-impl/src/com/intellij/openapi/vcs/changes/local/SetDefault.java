package com.intellij.openapi.vcs.changes.local;

import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListWorker;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.util.EventDispatcher;

public class SetDefault implements ChangeListCommand {
  private final String myNewDefaultName;
  private String myPrevious;
  private LocalChangeList myOldDefaultListCopy;
  private LocalChangeList myNewDefaultListCopy;

  public SetDefault(final String newDefaultName) {
    myNewDefaultName = newDefaultName;
  }

  public void apply(final ChangeListWorker worker) {
    myOldDefaultListCopy = worker.getDefaultListCopy();
    myPrevious = worker.setDefault(myNewDefaultName);
    myNewDefaultListCopy = worker.getDefaultListCopy();
  }

  public void doNotify(final EventDispatcher<ChangeListListener> dispatcher) {
    dispatcher.getMulticaster().defaultListChanged(myOldDefaultListCopy, myNewDefaultListCopy);
  }

  public String getPrevious() {
    return myPrevious;
  }
}
