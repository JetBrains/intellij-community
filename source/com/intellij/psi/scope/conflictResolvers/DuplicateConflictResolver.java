package com.intellij.psi.scope.conflictResolvers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.HashMap;

import java.util.Iterator;
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
    final Map<Object,PsiElement> uniqueItems = new HashMap<Object, PsiElement>();
    for (Iterator<CandidateInfo> iterator = conflicts.iterator(); iterator.hasNext();) {
      CandidateInfo info = iterator.next();
      final PsiElement element = info.getElement();
      Object key;
      if (element instanceof PsiMethod) {
        key = ((PsiMethod)element).getSignature(info.getSubstitutor());
      }
      else {
        key = PsiUtil.getName(element);
      }

      if (!uniqueItems.containsKey(key)) {
        uniqueItems.put(key, element);
      }
      else {
        iterator.remove();
      }
    }
    if(uniqueItems.size() == 1) return conflicts.get(0);
    return null;
  }

}
