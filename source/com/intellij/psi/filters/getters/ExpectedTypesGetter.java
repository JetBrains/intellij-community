package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.psi.*;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 09.04.2003
 * Time: 12:37:26
 * To change this template use Options | File Templates.
 */
public class ExpectedTypesGetter implements ContextGetter{

  public PsiType[] get(PsiElement context, CompletionContext completionContext){
    ExpectedTypesProvider typesProvider = ExpectedTypesProvider.getInstance(context.getProject());
    PsiExpression expression = PsiTreeUtil.getContextOfType(context, PsiExpression.class, true);
    if(expression == null) return PsiType.EMPTY_ARRAY;

    ExpectedTypeInfo[] infos = typesProvider.getExpectedTypes(expression, true);

    List<PsiType> result = new ArrayList<PsiType>(infos.length);
    for (ExpectedTypeInfo info : infos) {
      final PsiType type = info.getType();
      result.add(type);
      final PsiType defaultType = info.getDefaultType();
      if (!defaultType.equals(type)) {
        result.add(defaultType);
      }
    }
    return result.toArray(PsiType.EMPTY_ARRAY);
  }
}
