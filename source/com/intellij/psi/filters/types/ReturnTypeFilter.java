package com.intellij.psi.filters.types;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.util.IncorrectOperationException;
import org.jdom.Element;


/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.01.2003
 * Time: 20:05:52
 * To change this template use Options | File Templates.
 */
public class ReturnTypeFilter implements ElementFilter{
  private ElementFilter myFilter;

  public ReturnTypeFilter(ElementFilter filter){
    myFilter = filter;
  }
  public boolean isClassAcceptable(Class hintClass){
    return PsiVariable.class.isAssignableFrom(hintClass)
      || PsiMethod.class.isAssignableFrom(hintClass)
      || PsiExpression.class.isAssignableFrom(hintClass)
      || Template.class.isAssignableFrom(hintClass)
      || CandidateInfo.class.isAssignableFrom(hintClass);

  }

  public boolean isAcceptable(Object element, PsiElement context){
    PsiType type = null;
    if(element instanceof TemplateImpl){
      final TemplateImpl template = (TemplateImpl) element;
      String text = template.getTemplateText();
      StringBuffer resultingText = new StringBuffer(text);

      int segmentsCount = template.getSegmentsCount();

      for (int j = segmentsCount - 1; j >= 0; j--) {
        if (template.getSegmentName(j).equals("END")) {
          continue;
        }

        int segmentOffset = template.getSegmentOffset(j);

        resultingText.insert(segmentOffset, "xxx");
      }

      try {
        final PsiExpression templateExpression =
            context.getManager().getElementFactory().createExpressionFromText(resultingText.toString(), context);
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

  public void readExternal(Element element) throws InvalidDataException{
    //myFilter.readExternal(element);
  }

  public void writeExternal(Element element) throws WriteExternalException{
    //myFilter.writeExternal(element);
  }
}
