/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.lang.StdLanguages;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.jsp.jspJava.JspXmlTagBase;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProviderBase;
import com.intellij.psi.jsp.el.ELExpressionHolder;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.*;
import com.intellij.xml.util.XmlUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class IdReferenceProvider extends PsiReferenceProviderBase {
  @NonNls public static final String FOR_ATTR_NAME = "for";
  @NonNls public static final String ID_ATTR_NAME = "id";
  @NonNls public static final String STYLE_ID_ATTR_NAME = "styleId";
  @NonNls public static final String NAME_ATTR_NAME = "name";

  private static THashSet<String> ourNamespacesWithoutNameReference = new THashSet<String>();
  static {
    ourNamespacesWithoutNameReference.add( XmlUtil.JSP_URI );
    ourNamespacesWithoutNameReference.add( XmlUtil.STRUTS_BEAN_URI );
    ourNamespacesWithoutNameReference.add( XmlUtil.STRUTS_BEAN_URI2 );
    ourNamespacesWithoutNameReference.add( XmlUtil.STRUTS_LOGIC_URI );
    for(String s: XmlUtil.JSTL_CORE_URIS) ourNamespacesWithoutNameReference.add( s );
    ourNamespacesWithoutNameReference.add( "http://struts.apache.org/tags-tiles" );
    for(String s: MetaRegistry.SCHEMA_URIS) ourNamespacesWithoutNameReference.add( s );
  }

  public String[] getIdForAttributeNames() {
    return new String[]{FOR_ATTR_NAME, ID_ATTR_NAME, NAME_ATTR_NAME,STYLE_ID_ATTR_NAME};
  }

  public ElementFilter getIdForFilter() {
    return new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        final PsiElement grandParent = ((PsiElement)element).getParent().getParent();
        if (grandParent instanceof XmlTag) {
          final XmlTag tag = (XmlTag)grandParent;

          if (tag.getNamespacePrefix().length() > 0) {
            return true;
          }
        }
        return false;
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

      final PsiElement parentElement = element.getParent();
      if (!(parentElement instanceof XmlAttribute)) return PsiReference.EMPTY_ARRAY;
      final String name = ((XmlAttribute)parentElement).getName();
      final String ns = ((XmlAttribute)parentElement).getParent().getNamespace();
      final boolean jsfNs = com.intellij.xml.util.XmlUtil.JSF_CORE_URI.equals(ns) || com.intellij.xml.util.XmlUtil.JSF_HTML_URI.equals(ns);

      if (FOR_ATTR_NAME.equals(name)) {
        return new PsiReference[]{
          jsfNs && element.getText().indexOf(':') != -1 ? new IdRefReference(element, 1) {
            public boolean isSoft() {
              return true;
            }
          } :new IdRefReference(element, 1)
        };
      }
      else {
        final boolean allowReferences = !(ourNamespacesWithoutNameReference.contains(ns));
        
        if ((ID_ATTR_NAME.equals(name) && allowReferences) ||
             STYLE_ID_ATTR_NAME.equals(name) ||
             (NAME_ATTR_NAME.equals(name) && allowReferences)
            ) {
          final AttributeValueSelfReference attributeValueSelfReference;

          if (jsfNs) {
            attributeValueSelfReference = new AttributeValueSelfReference(element);
          } else {
            attributeValueSelfReference =  new GlobalAttributeValueSelfReference(element, true);
          }
          return new PsiReference[]{attributeValueSelfReference};
        }
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

  public static class GlobalAttributeValueSelfReference extends AttributeValueSelfReference {
    private boolean mySoft;

    public GlobalAttributeValueSelfReference(PsiElement element, boolean soft) {
      super(element);
      mySoft = soft;
    }

    public boolean isSoft() {
      return mySoft;
    }
  }
}
