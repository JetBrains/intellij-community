package com.intellij.psi.scope.processor;

import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.conflictResolvers.DuplicateConflictResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 31.01.2003
 * Time: 19:31:12
 * To change this template use Options | File Templates.
 */
public class MethodCandidatesProcessor extends MethodsProcessor{
  private PsiElement myPlace;

  protected MethodCandidatesProcessor(PsiElement place, PsiConflictResolver[] resolvers, List container){
    super(null, resolvers, container);
    myPlace = place;
  }

  public MethodCandidatesProcessor(PsiElement place){
    super(null, new PsiConflictResolver[]{new DuplicateConflictResolver()}, new ArrayList());
    myPlace = place;
  }

  public void add(PsiElement element, PsiSubstitutor substitutor) {
    if (element instanceof PsiMethod){
      final PsiMethod currentMethod = (PsiMethod)element;
      boolean staticProblem = isInStaticScope() && !currentMethod.hasModifierProperty(PsiModifier.STATIC);

      if ((!isConstructor() && getName().equals(currentMethod.getName()))
        || (currentMethod.isConstructor()
            && myAccessClass != null
            && myAccessClass == currentMethod.getContainingClass())){
        add(new MethodCandidateInfo(currentMethod, substitutor, myPlace, currentMethod.isConstructor() ? null : myAccessClass, staticProblem,
                                    getArgumentList(), myCurrentFileContext, getTypeArguments()));
      }
    }
  }

  public CandidateInfo[] getCandidates(){
    final ResolveResult[] resolveResult = getResult();
    CandidateInfo[] infos = new CandidateInfo[resolveResult.length];
    System.arraycopy(resolveResult, 0, infos, 0, resolveResult.length);
    return infos;
  }
}
