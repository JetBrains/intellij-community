/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.errorTreeView;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.StatusBarProgress;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 12, 2004
 */
public class ErrorViewTreeBuilder extends AbstractTreeBuilder{
  public ErrorViewTreeBuilder(JTree tree, DefaultTreeModel treeModel, AbstractTreeStructure treeStructure) {
    super(tree, treeModel, treeStructure, null);
  }

  public void updateFromRoot() {
    myUpdater.cancelAllRequests();
    super.updateFromRoot();
  }

  public void updateTree() {
    myUpdater.addSubtreeToUpdate(myRootNode);
  }

  public void updateTree(Runnable runAferUpdate) {
    myUpdater.runAfterUpdate(runAferUpdate);
    updateTree();
  }

  protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    final ErrorTreeElement element = (ErrorTreeElement)nodeDescriptor.getElement();
    if (element instanceof GroupingElement) {
      return ((ErrorViewStructure)myTreeStructure).getChildCount((GroupingElement)element) > 0;
    }
    return false;
  }

  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return nodeDescriptor.getParentDescriptor() == null || nodeDescriptor.getElement() instanceof GroupingElement;
  }

  @NotNull
  protected ProgressIndicator createProgressIndicator() {
    return new StatusBarProgress();
  }
}

