package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;

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
  public Object[] get(PsiElement context, CompletionContext completionContext){
    final List result = new ArrayList();
    final PsiExpression expression = PsiTreeUtil.getContextOfType(context, PsiExpression.class, true);
    if(expression == null) return ArrayUtil.EMPTY_OBJECT_ARRAY;

    ExpectedTypesProvider typesProvider = ExpectedTypesProvider.getInstance(expression.getProject());
    ExpectedTypeInfo[] infos = typesProvider.getExpectedTypes(expression, true);

    infos = extractUnique(infos, typesProvider);
    if (expression instanceof PsiNewExpression) {
      for(int i = 0; i < infos.length; i++) {
        result.add(CompletionUtil.eliminateWildcards(infos[i].getType()));
      }

    } else {
      for(int i = 0; i < infos.length; i++) {
        result.add(infos[i].getType());
      }
    }
    return (PsiType[]) result.toArray(new PsiType[result.size()]);
  }

  private ExpectedTypeInfo[] extractUnique(ExpectedTypeInfo[] infos, ExpectedTypesProvider typesProvider){
    ArrayList infoV = new ArrayList();
    AddInfosLoop:
      for(int i = 0; i < infos.length; i++) {
        ExpectedTypeInfo info = infos[i];
        PsiType type = info.getType();
        for(int j = 0; j < infoV.size(); j++) {
          ExpectedTypeInfo info1 = (ExpectedTypeInfo)infoV.get(j);
          PsiType type1 = info1.getType();
          if (type.equals(type1)){ //?
            if (info.getTailType() != info1.getTailType()){
              infoV.set(j, typesProvider.createInfo(type1, info1.getKind(), info1.getDefaultType(), TailType.NONE));
            }
            continue AddInfosLoop;
          }
        }
        infoV.add(info);
      }
    infos = (ExpectedTypeInfo[])infoV.toArray(new ExpectedTypeInfo[infoV.size()]);
    return infos;
  }
}
