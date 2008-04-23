package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.changes.ChangeListOwner;
import com.intellij.ui.SimpleTextAttributes;

public class ChangesBrowserLockedFoldersNode extends ChangesBrowserNode {
  public ChangesBrowserLockedFoldersNode(final Object userObject) {
    super(userObject);
  }

  public boolean canAcceptDrop(final ChangeListDragBean dragBean) {
    return false;
  }

  public void acceptDrop(final ChangeListOwner dragOwner, final ChangeListDragBean dragBean) {
  }

  public void render(final ChangesBrowserNodeRenderer renderer, final boolean selected, final boolean expanded, final boolean hasFocus) {
    renderer.append(userObject.toString(), SimpleTextAttributes.ERROR_ATTRIBUTES);
    appendCount(renderer);
  }
}
