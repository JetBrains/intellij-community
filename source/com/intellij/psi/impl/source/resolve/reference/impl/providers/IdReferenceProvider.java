/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProviderBase;
import com.intellij.psi.impl.source.jsp.jspJava.JspXmlTagBase;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.jsp.el.ELExpressionHolder;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public class IdReferenceProvider extends PsiReferenceProviderBase {
  @NonNls public static final String FOR_ATTR_NAME = "for";
  @NonNls public static final String ID_ATTR_NAME = "id";


  public String[] getIdForAttributeNames() {
    return new String[]{FOR_ATTR_NAME, ID_ATTR_NAME};
  }


  public ElementFilter getIdForFilter() {
    return new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        final PsiElement grandParent = ((PsiElement)element).getParent().getParent();
        return grandParent instanceof XmlTag && ((XmlTag)grandParent).getNamespacePrefix().length() > 0;
      }

      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    };
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    if (element instanceof XmlAttributeValue) {
      if (PsiTreeUtil.getChildOfAnyType(element, JspXmlTagBase.class, ELExpressionHolder.class) != null) {
        return PsiReference.EMPTY_ARRAY;
      }

      final String name = ((XmlAttribute)element.getParent()).getName();

      if (FOR_ATTR_NAME.equals(name)) {
        return new PsiReference[]{new IdRefReference(element, 1)};
      }
      else if (ID_ATTR_NAME.equals(name)) {
        return new PsiReference[]{new JspReferencesProvider.SelfReference(element)};
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

}
