package com.intellij.psi.scope.conflictResolvers;

import com.intellij.aspects.psi.PsiIntertypeField;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.jsp.JspFile;
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
      return (CandidateInfo) conflicts.get(0);
    }
    if(size > 0){
      final CandidateInfo[] uncheckedResult = (CandidateInfo[])conflicts.toArray(new CandidateInfo[size]);
      CandidateInfo currentResult = uncheckedResult[0];

      if(currentResult.getElement() instanceof PsiField){
        for(int i = 1; i < uncheckedResult.length; i++){
          //TODO[ik]: Handle introduced fields properly.
          final CandidateInfo candidate = uncheckedResult[i];
          if(currentResult == candidate || currentResult.getElement() == candidate.getElement()) continue;
          if(candidate.getElement() == null || candidate.getElement() instanceof PsiIntertypeField) continue;

          if (!(candidate.getElement() instanceof PsiField)) {
            if(candidate.getElement() instanceof PsiLocalVariable) {
              return candidate;
            }
            else {
              if (!currentResult.isAccessible()) return candidate;
              conflicts.remove(candidate);
              continue;
            }
          }

          final PsiClass newClass = ((PsiField)candidate.getElement()).getContainingClass();
          final PsiClass oldClass = ((PsiField)currentResult.getElement()).getContainingClass();

          // Hack for JSP
          if(newClass == null && candidate.getElement().getContainingFile() instanceof JspFile){
            conflicts.remove(currentResult);
            currentResult = candidate;
          }

          if(oldClass == null && currentResult.getElement().getContainingFile() instanceof JspFile){
            conflicts.remove(candidate);
            continue;
          }

          if(newClass.isInheritor(oldClass, true)){
            // current is better
            conflicts.remove(currentResult);
            currentResult = candidate;
          }
          else if(oldClass.isInheritor(newClass, true)){
              // current is worse
              conflicts.remove(candidate);
              continue;
            }
            else{
              if(!candidate.isAccessible()){
                conflicts.remove(candidate);
                continue;
              }
              if(!currentResult.isAccessible()){
                conflicts.remove(currentResult);
                currentResult = candidate;
                continue;
              }

              return null;
            }
          if(!candidate.isAccessible()){
            conflicts.remove(candidate);
            continue;
          }
          if(!currentResult.isAccessible()){
            conflicts.remove(currentResult);
            currentResult = candidate;
            continue;
          }
        }
      }
      return currentResult;
    }
    return null;
  }

  public void handleProcessorEvent(PsiScopeProcessor.Event event, Object associatied){}
}
