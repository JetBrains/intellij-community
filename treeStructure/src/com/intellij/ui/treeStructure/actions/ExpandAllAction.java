/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.treeStructure.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class ExpandAllAction extends AnAction {

  protected JTree myTree;

  public ExpandAllAction(JTree tree) {
    super("Expand All", "", IconLoader.getIcon("/actions/expandall.png"));
    myTree = tree;
  }

  public void actionPerformed(AnActionEvent e) {
    for (int i = 0; i < getTree().getRowCount(); i++) {
      getTree().expandRow(i);
    }
  }

  protected JTree getTree() {
    return myTree;
  }
}
