package com.intellij.psi.filters.getters;

import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.codeInsight.completion.CompletionContext;

import java.util.Set;
import java.util.HashSet;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 20.03.2003
 * Time: 21:29:59
 * To change this template use Options | File Templates.
 */
public class ThrowsListGetter implements ContextGetter{
  public Object[] get(PsiElement context, CompletionContext completionContext){
    final Set throwsSet = new HashSet();
    final PsiMethod method = PsiTreeUtil.getContextOfType(context, PsiMethod.class, true);
    if(method != null){
      final PsiClassType[] refs = method.getThrowsList().getReferencedTypes();
      for(int i = 0; i < refs.length; i++){
        final PsiClass exception = refs[i].resolve();
        if(exception != null)
          throwsSet.add(exception);
      }
    }
    return throwsSet.toArray();
  }
}
