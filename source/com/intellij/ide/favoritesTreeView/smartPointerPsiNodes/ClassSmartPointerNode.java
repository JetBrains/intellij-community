package com.intellij.ide.favoritesTreeView.smartPointerPsiNodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.PsiClassChildrenSource;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class ClassSmartPointerNode extends BaseSmartPointerPsiNode<SmartPsiElementPointer>{
  public ClassSmartPointerNode(Project project, PsiClass value, ViewSettings viewSettings) {
    super(project, SmartPointerManager.getInstance(project).createLazyPointer(value), viewSettings);
  }

  public ClassSmartPointerNode(Project project, Object value, ViewSettings viewSettings) {
    this(project, (PsiClass)value, viewSettings);
  }

  public Collection<AbstractTreeNode> getChildrenImpl() {
    PsiClass parent = getPsiClass();
    final ArrayList<AbstractTreeNode> treeNodes = new ArrayList<AbstractTreeNode>();

    ArrayList<PsiElement> result = new ArrayList<PsiElement>();
    if (getSettings().isShowMembers()) {
      PsiClassChildrenSource.DEFAULT_CHILDREN.addChildren(parent, result);
      for (Iterator<PsiElement> iterator = result.iterator(); iterator.hasNext();) {
        PsiElement psiElement = iterator.next();
        psiElement.accept(new PsiElementVisitor() {
          public void visitClass(PsiClass aClass) {
            treeNodes.add(new ClassSmartPointerNode(getProject(), aClass, getSettings()));
          }

          public void visitMethod(PsiMethod method) {
            treeNodes.add(new MethodSmartPointerNode(getProject(), method, getSettings()));
          }

          public void visitField(PsiField field) {
            treeNodes.add(new FieldSmartPointerNode(getProject(), field, getSettings()));
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
    final PsiClass value = getPsiClass();
    if (value != null) {
      data.setPresentableText(value.getName());
    }
  }

  public boolean isTopLevel() {
    if (getPsiElement() == null) return false;
    return getPsiElement().getParent() instanceof PsiFile;
  }


  public boolean expandOnDoubleClick() {
    return false;
  }

  public PsiClass getPsiClass() {
    return (PsiClass)getPsiElement();
  }

  public boolean isAlwaysExpand() {
    return getParentValue() instanceof PsiFile;
  }
}
