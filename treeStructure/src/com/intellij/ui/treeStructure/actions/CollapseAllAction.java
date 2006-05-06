/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.treeStructure.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class CollapseAllAction extends AnAction {

  private JTree myTree;

  public CollapseAllAction(JTree tree) {
    super("Collapse All", "", IconLoader.getIcon("/actions/collapseall.png"));
    myTree = tree;
  }

  public void actionPerformed(AnActionEvent e) {
    int row = getTree().getRowCount() - 1;
    while (row >= 0) {
      getTree().collapseRow(row);
      row--;
    }
  }

  protected JTree getTree() {
    return myTree;
  }
}
