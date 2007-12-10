package com.intellij.psi.filters.types;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ReflectionCache;
import org.jetbrains.annotations.NonNls;


/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.01.2003
 * Time: 20:05:52
 * To change this template use Options | File Templates.
 */
public class ReturnTypeFilter implements ElementFilter{
  private ElementFilter myFilter;
  @NonNls private static final String PLACEHOLDER = "xxx";

  public ReturnTypeFilter(ElementFilter filter){
    myFilter = filter;
  }
  public boolean isClassAcceptable(Class hintClass){
    return ReflectionCache.isAssignable(PsiVariable.class, hintClass) || ReflectionCache.isAssignable(PsiMethod.class, hintClass) ||
           ReflectionCache.isAssignable(PsiExpression.class, hintClass) || ReflectionCache.isAssignable(Template.class, hintClass) ||
           ReflectionCache.isAssignable(CandidateInfo.class, hintClass);

  }

  public boolean isAcceptable(Object element, PsiElement context){
    PsiType type = null;
    if(element instanceof TemplateImpl){
      final TemplateImpl template = (TemplateImpl) element;
      String text = template.getTemplateText();
      StringBuilder resultingText = new StringBuilder(text);

      int segmentsCount = template.getSegmentsCount();

      for (int j = segmentsCount - 1; j >= 0; j--) {
        if (template.getSegmentName(j).equals(TemplateImpl.END)) {
          continue;
        }

        int segmentOffset = template.getSegmentOffset(j);

        resultingText.insert(segmentOffset, PLACEHOLDER);
      }

      try {
        final PsiExpression templateExpression =
          JavaPsiFacade.getInstance(context.getProject()).getElementFactory().createExpressionFromText(resultingText.toString(), context);
        type = templateExpression.getType();
      }
      catch (IncorrectOperationException e) { // can happen when text of the template does not form an expression
      }
      if(type == null) return false;
    }

    if(type != null){
      return myFilter.isAcceptable(type, context);
    }
    return myFilter.isAcceptable(element, context);
  }

}
