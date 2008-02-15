package com.intellij.ide.projectView.impl.nodes;

import com.intellij.coverage.CoverageDataManager;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.PsiClassChildrenSource;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;

import java.util.ArrayList;
import java.util.Collection;

public class ClassTreeNode extends BasePsiMemberNode<PsiClass>{
  public ClassTreeNode(Project project, PsiClass value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  public Collection<AbstractTreeNode> getChildrenImpl() {
    PsiClass parent = getValue();
    final ArrayList<AbstractTreeNode> treeNodes = new ArrayList<AbstractTreeNode>();

    if (getSettings().isShowMembers()) {
      ArrayList<PsiElement> result = new ArrayList<PsiElement>();
      PsiClassChildrenSource.DEFAULT_CHILDREN.addChildren(parent, result);
      for (PsiElement psiElement : result) {
        psiElement.accept(new JavaElementVisitor() {
          @Override public void visitClass(PsiClass aClass) {
            treeNodes.add(new ClassTreeNode(getProject(), aClass, getSettings()));
          }

          @Override public void visitMethod(PsiMethod method) {
            treeNodes.add(new PsiMethodNode(getProject(), method, getSettings()));
          }

          @Override public void visitField(PsiField field) {
            treeNodes.add(new PsiFieldNode(getProject(), field, getSettings()));
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
    final PsiClass aClass = getValue();
    if (aClass != null) {
      data.setPresentableText(aClass.getName());
      final String qName = aClass.getQualifiedName();
      if (qName != null) {
        final CoverageDataManager coverageManager = CoverageDataManager.getInstance(aClass.getProject());
        final String coverageString = coverageManager.getClassCoverageInformationString(qName);
        data.setLocationString(coverageString);
      }
    }
  }

  public boolean isTopLevel() {
    return getValue() != null && getValue().getParent()instanceof PsiFile;
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

  public int getWeight() {
    return 20;
  }

  @Override
  public String getTitle() {
    final PsiClass psiClass = getValue();
    if (psiClass != null) {
      return psiClass.getQualifiedName();
    }
    return super.getTitle();
  }

  @Override
  protected boolean isMarkReadOnly() {
    return true;
  }

  public int getTypeSortWeight(final boolean sortByType) {
    return sortByType ? 5 : 0;
  }

  public Comparable getTypeSortKey() {
    return new ClassNameSortKey();
  }

  public static int getClassPosition(final PsiClass aClass) {
    if (aClass == null || !aClass.isValid()) {
      return 0;
    }
    int pos = ElementPresentationUtil.getClassKind(aClass);
    //abstract class before concrete
    if (pos == ElementPresentationUtil.CLASS_KIND_CLASS || pos == ElementPresentationUtil.CLASS_KIND_EXCEPTION) {
      boolean isAbstract = aClass.hasModifierProperty(PsiModifier.ABSTRACT) && !aClass.isInterface();
      if (isAbstract) {
        pos --;
      }
    }
    return pos;
  }

  private class ClassNameSortKey implements Comparable {
    public int compareTo(final Object o) {
      if (!(o instanceof ClassNameSortKey)) return 0;
      ClassNameSortKey rhs = (ClassNameSortKey) o;
      return getPosition() - rhs.getPosition();
    }

    int getPosition() {
      return getClassPosition(getValue());
    }
  }

  @Override
  public boolean shouldDrillDownOnEmptyElement() {
    return true;
  }
}
