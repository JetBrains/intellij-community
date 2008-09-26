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
import java.util.ArrayList;

/**
 * @author yole
 */
public class ChangesBrowserChangeListNode extends ChangesBrowserNode<ChangeList> {
  private ChangeListDecorator[] myDecorators;
  private final ChangeListManagerEx myClManager;

  public ChangesBrowserChangeListNode(Project project, ChangeList userObject) {
    super(userObject);
    myClManager = (ChangeListManagerEx) ChangeListManager.getInstance(project);
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
      if (myClManager.isInUpdate()) {
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

    final List<VirtualFile> toUpdate = new ArrayList<VirtualFile>();

    addIfNotNull(toUpdate, dragBean.getUnversionedFiles());
    addIfNotNull(toUpdate, dragBean.getIgnoredFiles());
    if (! toUpdate.isEmpty()) {
      dragOwner.addUnversionedFiles(dropList, toUpdate);
    }
  }

  private static void addIfNotNull(final List<VirtualFile> unversionedFiles1, final List<VirtualFile> ignoredFiles) {
    if (ignoredFiles != null) {
      unversionedFiles1.addAll(ignoredFiles);
    }
  }

  public int getSortWeight() {
    if (userObject instanceof LocalChangeList && ((LocalChangeList)userObject).isDefault()) return 1;
    return 2;
  }

  public int compareUserObjects(final Object o2) {
    if (o2 instanceof ChangeList) {
      return getUserObject().getName().compareToIgnoreCase(((ChangeList)o2).getName());
    }
    return 0;
  }
}