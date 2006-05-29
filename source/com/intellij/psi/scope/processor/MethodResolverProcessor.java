package com.intellij.psi.scope.processor;

import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.conflictResolvers.JavaMethodsConflictResolver;

import java.util.ArrayList;

public class MethodResolverProcessor extends MethodCandidatesProcessor implements NameHint, ElementClassHint, PsiResolverProcessor {
  private boolean myStopAcceptingCandidates = false;

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


  public void handleEvent(Event event, Object associated) {
    if (event == Event.CHANGE_LEVEL) {
      if (myHasAccessibleStaticCorrectCandidate) myStopAcceptingCandidates = true;
    }
    super.handleEvent(event, associated);
  }

  public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
    return !myStopAcceptingCandidates && super.execute(element, substitutor);
  }
}
