package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.RemoteRevisionsCache;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;

public class RemoteStatusChangeNodeDecorator implements ChangeNodeDecorator {
  protected final RemoteRevisionsCache myRemoteRevisionsCache;

  public RemoteStatusChangeNodeDecorator(final RemoteRevisionsCache remoteRevisionsCache) {
    myRemoteRevisionsCache = remoteRevisionsCache;
  }

  protected void reportState(final boolean state) {
  }

  public void decorate(final Change change, final SimpleColoredComponent component) {
    final boolean state = myRemoteRevisionsCache.getState(change);
    reportState(state);
    if (! state) {
      component.append(" ");
      component.append(VcsBundle.message("change.nodetitle.change.is.outdated"), SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
  }
}
