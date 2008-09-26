package com.intellij.openapi.vcs.changes.local;

import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListWorker;
import com.intellij.util.EventDispatcher;

public interface ChangeListCommand {
  void apply(final ChangeListWorker worker);
  void doNotify(final EventDispatcher<ChangeListListener> dispatcher);
}
