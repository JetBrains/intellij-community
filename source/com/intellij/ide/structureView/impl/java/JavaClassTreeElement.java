package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.AddAllMembersProcessor;
import com.intellij.psi.*;
import com.intellij.psi.scope.util.PsiScopesUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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

    List<PsiElement> children = Arrays.asList(aClass.getChildren());
    Collection<PsiElement> ownChildren = new THashSet<PsiElement>(children);
    Collection<PsiElement> inherited = new LinkedHashSet<PsiElement>(children);
    
    PsiScopesUtil.processScope(aClass, new AddAllMembersProcessor(inherited, aClass), PsiSubstitutor.UNKNOWN, null, aClass);

    for (PsiElement child : inherited) {
      if (!child.isValid()) continue;
      boolean isInherited = !ownChildren.contains(child);
      if (child instanceof PsiClass) {
        array.add(new JavaClassTreeElement((PsiClass)child, isInherited));
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
