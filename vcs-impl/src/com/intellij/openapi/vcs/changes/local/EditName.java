package com.intellij.openapi.vcs.changes.local;

import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListWorker;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

public class EditName implements ChangeListCommand {
  @NotNull
  private final String myFromName;
  @NotNull
  private final String myToName;
  private boolean myResult;
  private LocalChangeList myListCopy;

  public EditName(@NotNull final String fromName, @NotNull final String toName) {
    myFromName = fromName;
    myToName = toName;
  }

  public void apply(final ChangeListWorker worker) {
    myResult = worker.editName(myFromName, myToName);
    myListCopy = worker.getCopyByName(myToName);
  }

  public void doNotify(final EventDispatcher<ChangeListListener> dispatcher) {
    dispatcher.getMulticaster().changeListRenamed(myListCopy, myFromName);
  }

  public boolean isResult() {
    return myResult;
  }
}
