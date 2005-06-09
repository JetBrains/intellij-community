package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

public class FormNode extends ProjectViewNode<Form>{
  private final Collection<AbstractTreeNode> myChildren;
  public FormNode(Project project, Form value, ViewSettings viewSettings,
                  Collection<AbstractTreeNode> children) {
    super(project, value, viewSettings);
    myChildren = children;
  }

  public Collection<AbstractTreeNode> getChildren() {
    return myChildren;
  }

  public String getTestPresentation() {
    return "Form:" + getValue().getName();
  }

  public boolean contains(VirtualFile file) {
    for (Iterator<AbstractTreeNode> iterator = myChildren.iterator(); iterator.hasNext();) {
      ProjectViewNode treeNode = (ProjectViewNode)iterator.next();
      if (treeNode.contains(file)) return true;
    }
    return false;
  }

  public void update(PresentationData presentation) {
    if (getValue() == null || !getValue().isValid()) {
      setValue(null);
    } else {
      presentation.setPresentableText(getValue().getName());
      presentation.setIcons(StdFileTypes.GUI_DESIGNER_FORM.getIcon());
    }
  }

  public void navigate(final boolean requestFocus) {
    getValue().navigate(requestFocus);
  }

  public boolean canNavigate() {
    return getValue().canNavigate();
  }

  public boolean canNavigateToSource() {
    return getValue().canNavigateToSource();
  }

  public String getToolTip() {
    return "UI Designer Form";
  }

  public static AbstractTreeNode constructFormNode(final PsiManager psiManager,
                                             final PsiClass classToBind,
                                             final Project project,
                                             final ViewSettings settings) {
    final PsiFile[] formsBoundToClass = psiManager.getSearchHelper().findFormsBoundToClass(classToBind.getQualifiedName());
    final HashSet<AbstractTreeNode> children = new HashSet<AbstractTreeNode>();
    for (int i = 0; i < formsBoundToClass.length; i++) {
      PsiFile formBoundToClass = formsBoundToClass[i];
      children.add(new PsiFileNode(project, formBoundToClass, settings));
    }
    children.add(new ClassTreeNode(project, classToBind, settings));
    return new FormNode(project, new Form(classToBind, Arrays.asList(formsBoundToClass)), settings, children);
  }
}
