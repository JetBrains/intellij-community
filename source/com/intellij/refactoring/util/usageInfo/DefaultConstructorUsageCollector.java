package com.intellij.refactoring.util.usageInfo;

import com.intellij.psi.*;
import com.intellij.refactoring.util.RefactoringUtil;

import java.util.ArrayList;

public class DefaultConstructorUsageCollector implements RefactoringUtil.ImplicitConstructorUsageVisitor {
  private final ArrayList myUsages;

  public void visitConstructor(PsiMethod constructor) {
    myUsages.add(new DefaultConstructorImplicitUsageInfo(constructor));
  }

  public void visitClassWithoutConstructors(PsiClass aClass) {
    myUsages.add(new NoConstructorClassUsageInfo(aClass));
  }

  public DefaultConstructorUsageCollector(ArrayList result) {
    myUsages = result;
  }
}
