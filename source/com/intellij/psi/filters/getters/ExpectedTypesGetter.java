package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.util.PsiTreeUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

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
    return getExpectedTypes(context, false);
  }

  public static PsiType[] getExpectedTypes(final PsiElement context, boolean includeDefault) {
    ExpectedTypesProvider typesProvider = ExpectedTypesProvider.getInstance(context.getProject());
    PsiExpression expression = PsiTreeUtil.getContextOfType(context, PsiExpression.class, true);
    if(expression == null) return PsiType.EMPTY_ARRAY;

    ExpectedTypeInfo[] infos = typesProvider.getExpectedTypes(expression, true);

    Set<PsiType> result = new THashSet<PsiType>(infos.length);
    for (ExpectedTypeInfo info : infos) {
      final PsiType type = info.getType();
      result.add(type);
      final PsiType defaultType = info.getDefaultType();
      if (includeDefault && !defaultType.equals(type)) {
        result.add(defaultType);
      }
    }
    return result.toArray(new PsiType[result.size()]);
  }

}
