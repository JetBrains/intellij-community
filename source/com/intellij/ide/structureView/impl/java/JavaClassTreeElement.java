package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.AddAllMembersProcessor;
import com.intellij.psi.*;
import com.intellij.psi.scope.util.PsiScopesUtil;

import java.util.*;

public class JavaClassTreeElement extends JavaClassTreeElementBase<PsiClass> {
  public JavaClassTreeElement(PsiClass aClass, boolean inherited) {
    super(inherited,aClass);
  }

  public StructureViewTreeElement[] getChildrenBase() {
    Collection<StructureViewTreeElement> classChildren = getClassChildren();
    return classChildren.toArray(new StructureViewTreeElement[classChildren.size()]);
  }

  public Collection<StructureViewTreeElement> getClassChildren() {
    ArrayList<StructureViewTreeElement> array = new ArrayList<StructureViewTreeElement>();

    List<PsiElement> ownChildren = Arrays.asList(getElement().getChildren());
    ArrayList<PsiElement> inherited = new ArrayList<PsiElement>();
    for (Iterator<PsiElement> iterator = ownChildren.iterator(); iterator.hasNext();) {
      PsiElement psiElement = iterator.next();
      if (psiElement.isValid()) inherited.add(psiElement);
    }
    PsiScopesUtil.processScope(getElement(), new AddAllMembersProcessor(inherited, getElement(), new AddAllMembersProcessor.MemberFilter() {
      protected boolean isVisible(PsiModifierListOwner member) {
        return true;
      }
    }), PsiSubstitutor.UNKNOWN, null, getElement());

    for (Iterator iterator = inherited.iterator(); iterator.hasNext();) {
      PsiElement child = (PsiElement)iterator.next();
      if (child instanceof PsiClass || child instanceof PsiMethod || child instanceof PsiField) {
        if (child instanceof PsiClass) {
          if (child.isValid()) {
            array.add(new JavaClassTreeElement((PsiClass)child, !ownChildren.contains(child)));
          }
        }
        else if (child.isValid()){
          addMemeber(child, array, !ownChildren.contains(child));
        }
      }
    }
    return array;
  }

  private void addMemeber(PsiElement child, ArrayList<StructureViewTreeElement> array, boolean inherited) {
    if (child instanceof PsiField) {
      array.add(new PsiFieldTreeElement((PsiField)child, inherited));
    }
    else {
      array.add(new PsiMethodTreeElement((PsiMethod)child, inherited));
    }
  }

  public String getPresentableText() {
    return getElement().getName();
  }

  public boolean isPublic() {
    if (getElement().getParent() instanceof PsiFile){
      return true;
    } else {
      return super.isPublic();
    }
  }
}
