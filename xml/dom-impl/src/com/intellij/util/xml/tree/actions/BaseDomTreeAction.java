// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.xml.tree.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.util.xml.tree.DomModelTreeView;
import org.jetbrains.annotations.NotNull;

abstract public class BaseDomTreeAction extends AnAction {
  private DomModelTreeView myTreeView;

  protected BaseDomTreeAction() {
  }

  protected BaseDomTreeAction(DomModelTreeView treeView) {
    myTreeView = treeView;
  }

  @Override
  final public void update(@NotNull AnActionEvent e) {
    final DomModelTreeView treeView = getTreeView(e);

    if (treeView != null) {
      update(e, treeView);
    }
    else {
      e.getPresentation().setEnabled(false);
    }
  }

  protected DomModelTreeView getTreeView(AnActionEvent e) {
    if (myTreeView != null) return myTreeView;

    return e.getData(DomModelTreeView.DATA_KEY);
  }

  @Override
  final public void actionPerformed(@NotNull AnActionEvent e) {
    final DomModelTreeView treeView = getTreeView(e);
    if (treeView != null) {
      actionPerformed(e, treeView);
    }
  }

  public abstract void actionPerformed(AnActionEvent e, DomModelTreeView treeView);

  public abstract void update(AnActionEvent e, DomModelTreeView treeView);
}

