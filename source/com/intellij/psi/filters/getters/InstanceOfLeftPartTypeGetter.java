package com.intellij.psi.filters.getters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.filters.FilterUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.codeInsight.completion.CompletionContext;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 15.12.2003
 * Time: 17:39:34
 * To change this template use Options | File Templates.
 */
public class InstanceOfLeftPartTypeGetter implements ContextGetter {
  public Object[] get(PsiElement context, CompletionContext completionContext) {
    if((context = FilterUtil.getPreviousElement((PsiElement) context, true)) == null) return ArrayUtil.EMPTY_OBJECT_ARRAY;
    if(!PsiKeyword.INSTANCEOF.equals(context.getText())) return ArrayUtil.EMPTY_OBJECT_ARRAY;
    if((context = FilterUtil.getPreviousElement((PsiElement) context, false)) == null) return ArrayUtil.EMPTY_OBJECT_ARRAY;
    final PsiExpression contextOfType = PsiTreeUtil.getContextOfType(context, PsiExpression.class, false);
    return new Object[]{contextOfType.getType()};
  }
}
