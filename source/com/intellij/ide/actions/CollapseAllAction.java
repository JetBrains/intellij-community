package com.intellij.ide.actions;

import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;

public class CollapseAllAction extends TreeCollapseAllActionBase {
  protected TreeExpander getExpander(DataContext dataContext) {
    TreeExpander treeExpander = (TreeExpander)dataContext.getData(DataConstantsEx.TREE_EXPANDER);
    return treeExpander;
  }
}
