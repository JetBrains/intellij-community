package com.intellij.refactoring.ui;

import com.intellij.psi.*;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;

import java.util.ArrayList;
import java.util.HashSet;

class TypeListCreatingVisitor implements RefactoringHierarchyUtil.SuperTypeVisitor {
  private final ArrayList<PsiType> myList;
  private final PsiElementFactory myFactory;
  private final HashSet<PsiType> mySet;

  public TypeListCreatingVisitor(ArrayList<PsiType> result, PsiElementFactory factory) {
    myList = result;
    myFactory = factory;
    mySet = new HashSet<PsiType>();
  }

  public void visitType(PsiType aType) {
    if (!mySet.contains(aType)) {
      myList.add(aType);
      mySet.add(aType);
    }
  }

  public void visitClass(PsiClass aClass) {
    final PsiType type = myFactory.createType(aClass);
    if (!mySet.contains(type)) {
      myList.add(type);
      mySet.add(type);
    }
  }
}
