package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 09.04.2003
 * Time: 12:37:26
 * To change this template use Options | File Templates.
 */
public class ExpectedTypesGetter implements ContextGetter{

  @NotNull
  public PsiType[] get(PsiElement context, CompletionContext completionContext){
    return getExpectedTypes(context);
  }

  public static PsiType[] getExpectedTypes(final PsiElement context) {
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
    return result.toArray(new PsiType[result.size()]);
  }
}
