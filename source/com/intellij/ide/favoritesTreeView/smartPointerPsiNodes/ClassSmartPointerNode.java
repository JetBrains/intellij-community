package com.intellij.ide.favoritesTreeView.smartPointerPsiNodes;

import com.intellij.coverage.CoverageDataManager;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.PsiClassChildrenSource;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public class ClassSmartPointerNode extends BaseSmartPointerPsiNode<SmartPsiElementPointer>{
  public ClassSmartPointerNode(Project project, PsiClass value, ViewSettings viewSettings) {
    super(project, SmartPointerManager.getInstance(project).createLazyPointer(value), viewSettings);
  }

  public ClassSmartPointerNode(Project project, Object value, ViewSettings viewSettings) {
    this(project, (PsiClass)value, viewSettings);
  }

  @NotNull
  public Collection<AbstractTreeNode> getChildrenImpl() {
    PsiClass parent = getPsiClass();
    final ArrayList<AbstractTreeNode> treeNodes = new ArrayList<AbstractTreeNode>();

    ArrayList<PsiElement> result = new ArrayList<PsiElement>();
    if (getSettings().isShowMembers()) {
      PsiClassChildrenSource.DEFAULT_CHILDREN.addChildren(parent, result);
      for (PsiElement psiElement : result) {
        psiElement.accept(new JavaElementVisitor() {
          @Override public void visitClass(PsiClass aClass) {
            treeNodes.add(new ClassSmartPointerNode(getProject(), aClass, getSettings()));
          }

          @Override public void visitMethod(PsiMethod method) {
            treeNodes.add(new MethodSmartPointerNode(getProject(), method, getSettings()));
          }

          @Override public void visitField(PsiField field) {
            treeNodes.add(new FieldSmartPointerNode(getProject(), field, getSettings()));
          }

          @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
            visitExpression(expression);
          }
        });
      }
    }
    return treeNodes;
  }

  public void updateImpl(PresentationData data) {
    final PsiClass aClass = getPsiClass();
    if (aClass != null) {
      data.setPresentableText(aClass.getName());
      final String qName = aClass.getQualifiedName();
      if (qName != null) {
        final CoverageDataManager coverageManager = CoverageDataManager.getInstance(myProject);
        data.setLocationString(coverageManager.getClassCoverageInformationString(qName));
      }
    }
  }

  public boolean isTopLevel() {
    return getPsiElement() != null && getPsiElement().getParent() instanceof PsiFile;
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
