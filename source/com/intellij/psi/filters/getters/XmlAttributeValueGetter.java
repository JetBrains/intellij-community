package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.impl.source.jsp.jspJava.JspDirective;
import com.intellij.psi.impl.source.jsp.tagLibrary.TldUtil;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.xml.XmlAttributeDescriptor;

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
      final XmlAttribute attribute = (XmlAttribute)context;
      final XmlAttributeDescriptor descriptor = ((XmlAttribute)context).getDescriptor();

      if(descriptor != null) {
        final String[] values = descriptor.getEnumeratedValues();
        
        if((values == null || values.length==0)) {
          final PsiReference[] references = ((XmlAttribute)context).getValueElement().getReferences();
          if (references.length == 0) return getAllWordsFromDocument(context,completionContext);
          if (values == null) return new Object[0];
        }
        return values;
      }
    }
    return getAllWordsFromDocument(context, completionContext);
  }

  private static Object[] getAllWordsFromDocument(PsiElement context, CompletionContext completionContext) {
    AllWordsGetter getter = new AllWordsGetter();
    return getter.get(context, completionContext);
  }
}
