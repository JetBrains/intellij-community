package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;

public abstract class AbstractProjectTreeStructure extends ProjectAbstractTreeStructureBase implements ViewSettings {

  private AbstractTreeNode myRoot;

  public AbstractProjectTreeStructure(Project project) {
    super(project);
    myRoot = new ProjectViewProjectNode(myProject, this);
  }

  interface RootCreator {
    AbstractTreeNode createRoot(Project project, ViewSettings settings);
  }

  protected AbstractProjectTreeStructure(Project project, final RootCreator rootCreator) {
    super(project);
    myRoot = rootCreator.createRoot(project, this);
  }

  public abstract boolean isShowMembers();

  public final Object getRootElement() {
    return myRoot;
  }


  public final void commit() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
  }

  public final boolean hasSomethingToCommit() {
    return PsiDocumentManager.getInstance(myProject).hasUncommitedDocuments();
  }

  public boolean isStructureView() {
    return false;
  }

}