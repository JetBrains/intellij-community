package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.Form;
import com.intellij.ide.projectView.impl.nodes.FormNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;

import java.util.*;

public class FormMergerTreeStructureProvider implements TreeStructureProvider, ProjectComponent{
  private final Project myProject;

  public FormMergerTreeStructureProvider(Project project) {
    myProject = project;
  }

  public Collection<AbstractTreeNode> modify(AbstractTreeNode parent, Collection<AbstractTreeNode> children, ViewSettings settings) {
    if (parent.getValue() instanceof Form) return children;
    ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    ProjectViewNode[] copy = children.toArray(new ProjectViewNode[children.size()]);
    for (int i = 0; i < copy.length; i++) {
      ProjectViewNode element = copy[i];
      if (element.getValue() instanceof PsiClass){
        PsiClass aClass = ((PsiClass)element.getValue());
        PsiFile[] forms = aClass.getManager().getSearchHelper().findFormsBoundToClass(aClass.getQualifiedName());
        Collection<AbstractTreeNode> formNodes = findFormsIn(children, forms);
        if (formNodes.size() > 0) {
          Collection<PsiFile> formFiles = convertToFiles(formNodes);
          Collection<AbstractTreeNode> subNodes = new ArrayList<AbstractTreeNode>(formNodes);
          subNodes.add(element);
          result.add(new FormNode(myProject, new Form(aClass, formFiles), settings, subNodes));
          children.remove(element);
          children.removeAll(formNodes);
        }
      }
    }
    result.addAll(children);
    return result;
  }

  private Collection<PsiFile> convertToFiles(Collection<AbstractTreeNode> formNodes) {
    ArrayList<PsiFile> psiFiles = new ArrayList<PsiFile>();
    for (Iterator<AbstractTreeNode> iterator = formNodes.iterator(); iterator.hasNext();) {
      AbstractTreeNode treeNode = iterator.next();
      psiFiles.add((PsiFile)treeNode.getValue());
    }
    return psiFiles;
  }

  private Collection<AbstractTreeNode> findFormsIn(Collection<AbstractTreeNode> children, PsiFile[] forms) {
    ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    HashSet<PsiFile> psiFiles = new HashSet<PsiFile>(Arrays.asList(forms));
    for (Iterator<AbstractTreeNode> iterator = children.iterator(); iterator.hasNext();) {
      ProjectViewNode treeNode = (ProjectViewNode)iterator.next();
      if (psiFiles.contains(treeNode.getValue())) result.add(treeNode);
    }
    return result;
  }

  public void projectClosed() {
  }

  public void projectOpened() {
  }

  public void disposeComponent() {
  }

  public String getComponentName() {
    return "FormNodesProvider";
  }

  public void initComponent() {
  }
}

