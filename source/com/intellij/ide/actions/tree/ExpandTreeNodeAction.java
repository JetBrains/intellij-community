package com.intellij.ide.actions.tree;

import com.intellij.ui.TreeExpandCollapse;

import javax.swing.*;

public class ExpandTreeNodeAction extends BaseTreeNodeAction {
  protected void performOn(JTree tree) {
    TreeExpandCollapse.expand(tree);
  }
}
