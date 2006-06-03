package com.intellij.psi.scope.conflictResolvers;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.PsiScopeProcessor;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 10.06.2003
 * Time: 16:36:05
 * To change this template use Options | File Templates.
 */
public class JavaVariableConflictResolver implements PsiConflictResolver{
  public CandidateInfo resolveConflict(List<CandidateInfo> conflicts){
    final int size = conflicts.size();
    if(size == 1){
      return conflicts.get(0);
    }
    if(size > 0){
      final CandidateInfo[] uncheckedResult = conflicts.toArray(new CandidateInfo[size]);
      CandidateInfo currentResult = uncheckedResult[0];

      final PsiElement currentElement = currentResult.getElement();
      if(currentElement instanceof PsiField){
        for (int i = 1; i < uncheckedResult.length; i++) {
          final CandidateInfo candidate = uncheckedResult[i];
          final PsiElement otherElement = candidate.getElement();
          if (otherElement == null) continue;

          if (!(otherElement instanceof PsiField)) {
            if (otherElement instanceof PsiLocalVariable) {
              return candidate;
            }
            else {
              if (!currentResult.isAccessible()) return candidate;
              conflicts.remove(candidate);
              continue;
            }
          }

          final PsiClass newClass = ((PsiField)otherElement).getContainingClass();
          final PsiClass oldClass = ((PsiField)currentElement).getContainingClass();

          final PsiElement scope = currentResult.getCurrentFileResolveScope();
          if (newClass.isInheritor(oldClass, true)) {
            if (!(scope instanceof PsiClass) ||
                scope.equals(oldClass) ||
                scope.equals(newClass) ||
                !((PsiClass)scope).isInheritorDeep(oldClass, newClass)) {
              // candidate is better
              conflicts.remove(currentResult);
              currentResult = candidate;
              continue;
            }
          }
          else if (oldClass.isInheritor(newClass, true)) {
            if (!(scope instanceof PsiClass) ||
                scope.equals(oldClass) ||
                scope.equals(newClass) ||
                !((PsiClass)scope).isInheritorDeep(newClass, oldClass)) {
              // candidate is worse
              conflicts.remove(candidate);
              continue;
            }
          }

          if (!candidate.isAccessible()) {
            conflicts.remove(candidate);
            continue;
          }
          if (!currentResult.isAccessible()) {
            conflicts.remove(currentResult);
            currentResult = candidate;
            continue;
          }

          //This test should go last
          if (otherElement == currentElement && candidate.getSubstitutor().equals(currentResult.getSubstitutor())) {
            conflicts.remove(candidate);
            continue;
          }

          return null;
        }
      }
      return currentResult;
    }
    return null;
  }

  public void handleProcessorEvent(PsiScopeProcessor.Event event, Object associatied){}
}
