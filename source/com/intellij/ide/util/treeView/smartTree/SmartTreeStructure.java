package com.intellij.ide.util.treeView.smartTree;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;

public class SmartTreeStructure extends AbstractTreeStructure {

  protected final TreeModel myModel;
  protected final Project myProject;
  private TreeElementWrapper myRootElementWrapper;

  private static Logger LOG = Logger.getInstance("#com.intellij.ide.util.treeView.smartTree.SmartTreeStructure");

  public SmartTreeStructure(Project project, TreeModel model) {

    LOG.assertTrue(model != null);

    myModel = model;
    myProject = project;

  }

  public void commit() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
  }

  @NotNull
  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    return (AbstractTreeNode)element;
  }

  public Object[] getChildElements(Object element) {
    return ((AbstractTreeNode)element).getChildren().toArray();
  }

  public Object getParentElement(Object element) {
    return ((AbstractTreeNode)element).getParent();
  }

  public Object getRootElement() {
    if (myRootElementWrapper == null){
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      myRootElementWrapper = createTree();
    }
    return myRootElementWrapper;
  }

  protected TreeElementWrapper createTree() {
    return new TreeElementWrapper(myProject, myModel.getRoot(), myModel);
  }

  public boolean hasSomethingToCommit() {
    return PsiDocumentManager.getInstance(myProject).hasUncommitedDocuments();
  }

  public void rebuildTree() {
    ((CachingChildrenTreeNode)getRootElement()).rebuildChildren();
  }
}
