// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.idea.svn.api.Url;

import javax.swing.tree.TreeNode;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class RepositoryTreeRootNode implements TreeNode, Disposable {
  private final List<TreeNode> myChildren;
  private final RepositoryTreeModel myModel;

  public RepositoryTreeRootNode(RepositoryTreeModel model, Url[] urls) {
    myChildren = new ArrayList<>();
    myModel = model;

    for (Url url : urls) {
      RepositoryTreeNode rootNode = new RepositoryTreeNode(model, this, url, url);
      Disposer.register(this, rootNode);
      myChildren.add(rootNode);
    }
    myChildren.sort((o1, o2) -> Collator.getInstance().compare(o1.toString(), o2.toString()));
  }

  public void addRoot(Url url) {
    RepositoryTreeNode rootNode = new RepositoryTreeNode(myModel, this, url, url);
    Disposer.register(this, rootNode);
    myChildren.add(rootNode);
    myChildren.sort((o1, o2) -> Collator.getInstance().compare(o1.toString(), o2.toString()));
    myModel.nodesWereInserted(this, new int[]{myChildren.indexOf(rootNode)});
  }

  public void remove(TreeNode node) {
    int index = getIndex(node);
    myChildren.remove(node);
    myModel.nodesWereRemoved(this, new int[]{index}, new Object[]{node});
  }

  @Override
  public Enumeration children() {
    return Collections.enumeration(myChildren);
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public TreeNode getChildAt(int childIndex) {
    return myChildren.get(childIndex);
  }

  @Override
  public int getChildCount() {
    return myChildren.size();
  }

  @Override
  public int getIndex(TreeNode node) {
    return myChildren.indexOf(node);
  }

  @Override
  public TreeNode getParent() {
    return null;
  }

  @Override
  public boolean isLeaf() {
    return false;
  }

  @Override
  public void dispose() {
  }
}
