/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.ChangeListDecorator;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleTextAttributes;

/**
 * @author yole
 */
public class ChangesBrowserChangeListNode extends ChangesBrowserNode {
  private ChangeListDecorator[] myDecorators;

  public ChangesBrowserChangeListNode(Project project, ChangeList userObject) {
    super(userObject);
    myDecorators = project.getComponents(ChangeListDecorator.class);
  }

  @Override
  public void render(final ChangesBrowserNodeRenderer renderer, final boolean selected, final boolean expanded, final boolean hasFocus) {
    if (userObject instanceof LocalChangeList) {
      final LocalChangeList list = ((LocalChangeList)userObject);
      renderer.append(list.getName(),
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
      final ChangeList list = ((ChangeList)userObject);
      renderer.append(list.getName(), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
      appendCount(renderer);
    }
  }
}