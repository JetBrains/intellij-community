package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.AddAllMembersProcessor;
import com.intellij.psi.*;
import com.intellij.psi.scope.util.PsiScopesUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class JavaClassTreeElement extends JavaClassTreeElementBase<PsiClass> {
  public JavaClassTreeElement(PsiClass aClass, boolean inherited) {
    super(inherited,aClass);
  }

  @NotNull
  public Collection<StructureViewTreeElement> getChildrenBase() {
    return getClassChildren();
  }

  private Collection<StructureViewTreeElement> getClassChildren() {
    ArrayList<StructureViewTreeElement> array = new ArrayList<StructureViewTreeElement>();

    final PsiClass aClass = getElement();
    if (aClass == null) return array;
    
    List<PsiElement> ownChildren = Arrays.asList(aClass.getChildren());
    List<PsiElement> inherited = new ArrayList<PsiElement>(ownChildren);
    
    PsiScopesUtil.processScope(aClass, new AddAllMembersProcessor(inherited, aClass), PsiSubstitutor.UNKNOWN, null, aClass);

    for (PsiElement child : inherited) {
      if (!child.isValid()) continue;
      boolean isInherited = !ownChildren.contains(child);
      if (child instanceof PsiClass) {
        array.add(new JavaClassTreeElement((PsiClass)child, !ownChildren.contains(child)));
      }
      else if (child instanceof PsiField) {
        array.add(new PsiFieldTreeElement((PsiField)child, isInherited));
      }
      else if (child instanceof PsiMethod) {
        array.add(new PsiMethodTreeElement((PsiMethod)child, isInherited));
      }
      else if (child instanceof PsiClassInitializer) {
        array.add(new ClassInitializerTreeElement((PsiClassInitializer)child));
      }
    }
    return array;
  }

  public String getPresentableText() {
    return getElement().getName();
  }

  public boolean isPublic() {
    return getElement().getParent() instanceof PsiFile || super.isPublic();
  }
}
