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
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.util.XmlUtil;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 24.11.2003
 * Time: 14:17:59
 * To change this template use Options | File Templates.
 */
public class HtmlAttributeValueGetter extends XmlAttributeValueGetter {
  private boolean myCaseSensitive;

  public HtmlAttributeValueGetter(boolean _caseSensitive) {
    myCaseSensitive = _caseSensitive;
  }

  protected String[] addSpecificCompletions(final PsiElement context) {
    if (!(context instanceof XmlAttribute)) return null;
    
    XmlAttribute attribute = (XmlAttribute)context;
    String name = attribute.getName();
    if (name == null) return null;
    if (!myCaseSensitive) name = name.toLowerCase();

    final String namespace = attribute.getParent().getNamespace();
    if (XmlUtil.XHTML_URI.equals(namespace) || XmlUtil.HTML_URI.equals(namespace)) {
      
      //noinspection HardCodedStringLiteral
      if ("target".equals(name)) {
        //noinspection HardCodedStringLiteral
        return new String[] {"_blank","_top","_self","_parent"};
      } else if ("enctype".equals(name)) {
        return new String[] {"multipart/form-data","application/x-www-form-urlencoded"};
      }
    }

    return null;
  }
}
