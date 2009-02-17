package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.psi.*;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.filters.FilterUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 15.12.2003
 * Time: 17:39:34
 * To change this template use Options | File Templates.
 */
public class InstanceOfLeftPartTypeGetter implements ContextGetter {
  public Object[] get(PsiElement context, CompletionContext completionContext) {
    if((context = FilterUtil.getPreviousElement(context, true)) == null) return ArrayUtil.EMPTY_OBJECT_ARRAY;
    if(!PsiKeyword.INSTANCEOF.equals(context.getText())) return ArrayUtil.EMPTY_OBJECT_ARRAY;
    if((context = FilterUtil.getPreviousElement(context, false)) == null) return ArrayUtil.EMPTY_OBJECT_ARRAY;
    final PsiExpression contextOfType = PsiTreeUtil.getContextOfType(context, PsiExpression.class, false);
    PsiType type = contextOfType.getType();
    if (type == null) return ArrayUtil.EMPTY_OBJECT_ARRAY;

    if (type instanceof PsiClassType) {
      final PsiClass psiClass = ((PsiClassType)type).resolve();
      if (psiClass instanceof PsiTypeParameter) {
        return psiClass.getExtendsListTypes();
      }
    }

    return new Object[]{type};
  }
}
