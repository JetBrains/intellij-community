package com.intellij.ide.util.treeView.smartTree;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;

public class SmartTreeStructure extends AbstractTreeStructure {

  private final TreeModel myModel;
  private final Project myProject;
  private TreeElementWrapper myRootElementWrapper;

  private static Logger LOG = Logger.getInstance("#com.intellij.ide.util.treeView.smartTree.SmartTreeStructure");

  public SmartTreeStructure(Project project, TreeModel model) {

    LOG.assertTrue(model != null);

    myModel = model;
    myProject = project;

  }

  public void commit() {
  }

  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    return ((AbstractTreeNode)element);
  }

  public Object[] getChildElements(Object element) {
    return ((AbstractTreeNode)element).getChildren().toArray();
  }

  public Object getParentElement(Object element) {
    return ((AbstractTreeNode)element).getParent();
  }

  public Object getRootElement() {
    if (myRootElementWrapper == null){
      myRootElementWrapper = createTree();
    }
    return myRootElementWrapper;
  }

  private TreeElementWrapper createTree() {
      return new TreeElementWrapper(myProject, myModel.getRoot(), myModel);
  }

  public boolean hasSomethingToCommit() {
    return false;
  }

  public void rebuildTree() {
    if (myRootElementWrapper != null) {
      final TreeElementWrapper newRoot = createTree();
      newRoot.myOldChildren = myRootElementWrapper.myChildren;
      newRoot.synchronizeChildren();
    }
    ((CachingChildrenTreeNode)getRootElement()).rebuildChildren();
  }
}
