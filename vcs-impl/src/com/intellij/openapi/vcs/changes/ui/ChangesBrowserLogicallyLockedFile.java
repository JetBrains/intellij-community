package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.LogicalLock;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;

public class ChangesBrowserLogicallyLockedFile extends ChangesBrowserFileNode {
  private final LogicalLock myLogicalLock;

  public ChangesBrowserLogicallyLockedFile(Project project, VirtualFile userObject, LogicalLock logicalLock) {
    super(project, userObject);
    myLogicalLock = logicalLock;
  }

  @Override
  public void render(ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    super.render(renderer, selected, expanded, hasFocus);
    renderer.append(" locked by ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
    renderer.append(myLogicalLock.getOwner(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }
}
