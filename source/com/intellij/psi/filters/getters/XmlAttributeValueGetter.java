package com.intellij.psi.filters.getters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.completion.WordCompletionData;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 24.11.2003
 * Time: 14:17:59
 * To change this template use Options | File Templates.
 */
public class XmlAttributeValueGetter implements ContextGetter {
  public XmlAttributeValueGetter() {}

  public Object[] get(PsiElement context, CompletionContext completionContext) {
    return getApplicableAttributeVariants(context, completionContext);
  }

  public static Object[] getApplicableAttributeVariants(PsiElement context, CompletionContext completionContext) {
    if(context != null)
      context = PsiTreeUtil.getParentOfType(context, XmlAttribute.class);

    if(context instanceof XmlAttribute){
      XmlAttributeDescriptor jspTagAttribute = ((XmlAttribute)context).getDescriptor();

      if(jspTagAttribute != null){
        final String[] values = jspTagAttribute.getEnumeratedValues();
        
        if((values == null || values.length==0)) {
          final PsiReference[] references = ((XmlAttribute)context).getValueElement().getReferences();
          if (references.length == 0) return getAllWordsFromDocument(context,completionContext);
        }
        return values;
      }
    }
    return getAllWordsFromDocument(context, completionContext);
  }

  private static Object[] getAllWordsFromDocument(PsiElement context, CompletionContext completionContext) {
    WordCompletionData.AllWordsGetter getter = new WordCompletionData.AllWordsGetter();
    return getter.get(context, completionContext);
  }
}
