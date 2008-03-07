package com.intellij.psi.scope.processor;

import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.conflictResolvers.JavaMethodsConflictResolver;
import com.intellij.util.ReflectionCache;
import com.intellij.util.SmartList;

public class MethodResolverProcessor extends MethodCandidatesProcessor implements ElementClassHint {
  private boolean myStopAcceptingCandidates = false;

  public MethodResolverProcessor(PsiMethodCallExpression place){
    super(place, new PsiConflictResolver[]{new JavaMethodsConflictResolver(place.getArgumentList())}, new SmartList<CandidateInfo>());
    setArgumentList(place.getArgumentList());
    obtainTypeArguments(place);
  }

  public MethodResolverProcessor(PsiClass classConstr, PsiExpressionList argumentList, PsiElement place) {
    super(place, new PsiConflictResolver[]{new JavaMethodsConflictResolver(argumentList)}, new SmartList<CandidateInfo>());
    setIsConstructor(true);
    setAccessClass(classConstr);
    setArgumentList(argumentList);
  }

  public boolean shouldProcess(Class elementClass) {
    return ReflectionCache.isAssignable(PsiMethod.class, elementClass);
  }


  public void handleEvent(Event event, Object associated) {
    if (event == JavaScopeProcessorEvent.CHANGE_LEVEL) {
      if (myHasAccessibleStaticCorrectCandidate) myStopAcceptingCandidates = true;
    }
    super.handleEvent(event, associated);
  }

  public boolean execute(PsiElement element, ResolveState state) {
    return !myStopAcceptingCandidates && super.execute(element, state);
  }
}
