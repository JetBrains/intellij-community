package com.intellij.psi.scope.conflictResolvers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.containers.HashMap;

import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 31.03.2003
 * Time: 17:21:42
 * To change this template use Options | File Templates.
 */
public class DuplicateConflictResolver implements PsiConflictResolver{
  public CandidateInfo resolveConflict(List<CandidateInfo> conflicts){
    final Map<Object, CandidateInfo> uniqueItems = new HashMap<Object, CandidateInfo>();
    for (CandidateInfo info : conflicts) {
      final PsiElement element = info.getElement();
      Object key;
      if (element instanceof PsiMethod) {
        key = ((PsiMethod)element).getSignature(info.getSubstitutor());
      }
      else {
        key = PsiUtilBase.getName(element);
      }

      if (!uniqueItems.containsKey(key)) {
        uniqueItems.put(key, info);
      }
    }

    if(uniqueItems.size() == 1) return uniqueItems.values().iterator().next();
    return null;
  }

}
