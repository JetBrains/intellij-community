package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.AddAllMembersProcessor;
import com.intellij.psi.*;
import com.intellij.psi.scope.util.PsiScopesUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class JavaClassTreeElement extends JavaClassTreeElementBase<PsiClass> {
  public JavaClassTreeElement(PsiClass aClass, boolean inherited) {
    super(inherited,aClass);
  }

  public Collection<StructureViewTreeElement> getChildrenBase() {
    Collection<StructureViewTreeElement> classChildren = getClassChildren();
    return classChildren;
  }

  private Collection<StructureViewTreeElement> getClassChildren() {
    ArrayList<StructureViewTreeElement> array = new ArrayList<StructureViewTreeElement>();

    final PsiClass aClass = getElement();
    if (aClass == null) return array;
    
    List<PsiElement> ownChildren = Arrays.asList(aClass.getChildren());
    List<PsiElement> inherited = new ArrayList<PsiElement>(ownChildren);
    
    PsiScopesUtil.processScope(aClass, new AddAllMembersProcessor(inherited, aClass), PsiSubstitutor.UNKNOWN, null, aClass);

    for (int i = 0; i < inherited.size(); i++) {
      PsiElement child = inherited.get(i);
      if (!child.isValid()) continue;
      if (child instanceof PsiClass) {
        array.add(new JavaClassTreeElement((PsiClass)child, !ownChildren.contains(child)));
      }
      else if (child instanceof PsiMethod || child instanceof PsiField){
        addMember(child, array, !ownChildren.contains(child));
      }
    }
    return array;
  }

  private void addMember(PsiElement child, ArrayList<StructureViewTreeElement> array, boolean inherited) {
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
