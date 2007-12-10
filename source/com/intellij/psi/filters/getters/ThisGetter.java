package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.psi.*;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 05.12.2003
 * Time: 14:02:59
 * To change this template use Options | File Templates.
 */
public class ThisGetter implements ContextGetter{
  public Object[] get(PsiElement context, CompletionContext completionContext) {
    boolean first = true;
    final List<PsiExpression> expressions = new ArrayList<PsiExpression>();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();

    while(context != null){
      if(context instanceof PsiClass){
        final String expressionText;
        if(first){
          first = false;
          expressionText = PsiKeyword.THIS;
        }
        else expressionText = ((PsiClass)context).getName() + "." + PsiKeyword.THIS;
        try{
          expressions.add(factory.createExpressionFromText(expressionText, context));
        }
        catch(IncorrectOperationException ioe){}
      }
      if(context instanceof PsiModifierListOwner){
        if(((PsiModifierListOwner)context).hasModifierProperty(PsiModifier.STATIC)) break;
      }
      context = context.getContext();
    }
    return expressions.toArray();
  }
}
