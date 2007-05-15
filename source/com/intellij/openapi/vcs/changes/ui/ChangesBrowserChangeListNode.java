/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;

import java.util.List;

/**
 * @author yole
 */
public class ChangesBrowserChangeListNode extends ChangesBrowserNode<ChangeList> {
  private ChangeListDecorator[] myDecorators;

  public ChangesBrowserChangeListNode(Project project, ChangeList userObject) {
    super(userObject);
    myDecorators = project.getComponents(ChangeListDecorator.class);
  }

  @Override
  public void render(final ChangesBrowserNodeRenderer renderer, final boolean selected, final boolean expanded, final boolean hasFocus) {
    if (userObject instanceof LocalChangeList) {
      final LocalChangeList list = ((LocalChangeList)userObject);
      renderer.appendTextWithIssueLinks(list.getName(),
             list.isDefault() ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
      appendCount(renderer);
      for(ChangeListDecorator decorator: myDecorators) {
        decorator.decorateChangeList(list, renderer, selected, expanded, hasFocus);
      }
      if (list.isInUpdate()) {
        renderer.append(" " + VcsBundle.message("changes.nodetitle.updating"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    }
    else {
      renderer.append(getUserObject().getName(), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
      appendCount(renderer);
    }
  }

  @Override
  public String getTextPresentation() {
    return getUserObject().getName().trim();
  }

  @Override
  public boolean canAcceptDrop(final ChangeListDragBean dragBean) {
    final Change[] changes = dragBean.getChanges();
    for (Change change : getUserObject().getChanges()) {
      for (Change incomingChange : changes) {
        if (change == incomingChange) return false;
      }
    }

    return true;
  }

  @Override
  public void acceptDrop(final ChangeListOwner dragOwner, final ChangeListDragBean dragBean) {
    if (!(userObject instanceof LocalChangeList)) {
      return;
    }
    final LocalChangeList dropList = (LocalChangeList)getUserObject();
    dragOwner.moveChangesTo(dropList, dragBean.getChanges());
    final List<VirtualFile> unversionedFiles = dragBean.getUnversionedFiles();
    if (unversionedFiles != null) {
      dragOwner.addUnversionedFiles(dropList, unversionedFiles);
    }
    final List<VirtualFile> ignoredFiles = dragBean.getIgnoredFiles();
    if (ignoredFiles != null) {
      dragOwner.addUnversionedFiles(dropList, ignoredFiles);
    }
  }
}