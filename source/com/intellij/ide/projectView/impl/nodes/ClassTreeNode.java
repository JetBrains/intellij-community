package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.PsiClassChildrenSource;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class ClassTreeNode extends BasePsiNode<PsiClass>{
  public ClassTreeNode(Project project, PsiClass value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  public Collection<AbstractTreeNode> getChildrenImpl() {
    PsiClass parent = getValue();
    final ArrayList<AbstractTreeNode> treeNodes = new ArrayList<AbstractTreeNode>();

    ArrayList<PsiElement> result = new ArrayList<PsiElement>();
    if (getSettings().isShowMembers()) {
      PsiClassChildrenSource.DEFAULT_CHILDREN.addChildren((PsiClass)parent, result);
      for (Iterator<PsiElement> iterator = result.iterator(); iterator.hasNext();) {
        PsiElement psiElement = iterator.next();
        psiElement.accept(new PsiElementVisitor() {
          public void visitClass(PsiClass aClass) {
            treeNodes.add(new ClassTreeNode(getProject(), aClass, getSettings()));
          }

          public void visitMethod(PsiMethod method) {
            treeNodes.add(new PsiMethodNode(getProject(), method, getSettings()));
          }

          public void visitField(PsiField field) {
            treeNodes.add(new PsiFieldNode(getProject(), field, getSettings()));
          }
        });
      }
    }
    return treeNodes;
  }

  public void updateImpl(PresentationData data) {
    data.setPresentableText(getValue().getName());
  }

  public boolean isTopLevel() {
    if (getValue() == null) return false;
    return getValue().getParent() instanceof PsiFile;
  }


  public boolean expandOnDoubleClick() {
    return false;
  }

  public PsiClass getPsiClass() {
    return getValue();
  }

  public boolean isAlwaysExpand() {
    return getParentValue() instanceof PsiFile;
  }
}
