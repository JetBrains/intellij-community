/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.tree.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.util.xml.tree.DomModelTreeView;

/**
 * User: Sergey.Vasiliev
 */
abstract public class BaseDomTreeAction extends AnAction  {
private DomModelTreeView myTreeView;

  protected BaseDomTreeAction() {
  }

  protected BaseDomTreeAction(DomModelTreeView treeView) {
    myTreeView = treeView;
  }

  final public void update(AnActionEvent e) {
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

    return (DomModelTreeView)e.getDataContext().getData(DomModelTreeView.DOM_MODEL_TREE_VIEW_KEY);
  }

  final public void actionPerformed(AnActionEvent e) {
    final DomModelTreeView treeView = getTreeView(e);
    if (treeView != null) {
      actionPerformed(e, treeView);
    }
  }

  public abstract void actionPerformed(AnActionEvent e, DomModelTreeView treeView);

  public  abstract void update(AnActionEvent e, DomModelTreeView treeView);
}

