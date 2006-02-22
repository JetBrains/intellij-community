package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.PsiClassChildrenSource;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

import java.util.ArrayList;
import java.util.Collection;

public class ClassTreeNode extends BasePsiNode<PsiClass>{
  public ClassTreeNode(Project project, PsiClass value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  public ClassTreeNode(Project project, Object value, ViewSettings viewSettings) {
    this(project, (PsiClass)value, viewSettings);
  }

  public Collection<AbstractTreeNode> getChildrenImpl() {
    PsiClass parent = getValue();
    final ArrayList<AbstractTreeNode> treeNodes = new ArrayList<AbstractTreeNode>();

    ArrayList<PsiElement> result = new ArrayList<PsiElement>();
    if (getSettings().isShowMembers()) {
      PsiClassChildrenSource.DEFAULT_CHILDREN.addChildren(parent, result);
      for (PsiElement psiElement : result) {
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

          public void visitReferenceExpression(PsiReferenceExpression expression) {
            visitExpression(expression);
          }
        });
      }
    }
    return treeNodes;
  }

  public void updateImpl(PresentationData data) {
    final PsiClass value = getValue();
    if (value != null) {
      data.setPresentableText(value.getName());
    }
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
