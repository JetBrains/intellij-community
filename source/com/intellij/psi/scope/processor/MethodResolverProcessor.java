package com.intellij.psi.scope.processor;

import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.conflictResolvers.JavaMethodsConflictResolver;

import java.util.ArrayList;

public class MethodResolverProcessor extends MethodCandidatesProcessor implements NameHint, ElementClassHint, PsiResolverProcessor {
  public MethodResolverProcessor(PsiMethodCallExpression place){
    super(place, new PsiConflictResolver[]{new JavaMethodsConflictResolver(place.getArgumentList())}, new ArrayList());
    setArgumentList(place.getArgumentList());
    obtainTypeArguments(place);
  }

  public MethodResolverProcessor(PsiClass classConstr, PsiExpressionList argumentList, PsiElement place) {
    super(place, new PsiConflictResolver[]{new JavaMethodsConflictResolver(argumentList)}, new ArrayList());
    setIsConstructor(true);
    setAccessClass(classConstr);
    setArgumentList(argumentList);
  }

  public String getProcessorType(){
    return "method resolver";
  }

  public boolean shouldProcess(Class elementClass) {
    return PsiMethod.class.isAssignableFrom(elementClass);
  }
}
