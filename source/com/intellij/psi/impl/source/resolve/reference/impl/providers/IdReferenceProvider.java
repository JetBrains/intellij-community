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
  private static THashSet<String> ourNamespacesWithoutIdRefs = new THashSet<String>();
  static {
    ourNamespacesWithoutIdRefs.add( com.intellij.xml.util.XmlUtil.JSP_URI );
    ourNamespacesWithoutIdRefs.add( com.intellij.xml.util.XmlUtil.STRUTS_BEAN_URI );
    ourNamespacesWithoutIdRefs.add( com.intellij.xml.util.XmlUtil.STRUTS_BEAN_URI2 );
    ourNamespacesWithoutIdRefs.add( com.intellij.xml.util.XmlUtil.STRUTS_LOGIC_URI );
    for(String s:com.intellij.xml.util.XmlUtil.JSTL_CORE_URIS) ourNamespacesWithoutIdRefs.add( s );
    ourNamespacesWithoutIdRefs.add( "http://struts.apache.org/tags-tiles" );
    for(String s: MetaRegistry.SCHEMA_URIS) ourNamespacesWithoutIdRefs.add( s );
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
          final String nsPrefix = tag.getNamespacePrefix();

          if (nsPrefix.length() > 0) {
            String ns = tag.getNamespace();
            if (ns.endsWith("-el")) ns = ns.substring(0, ns.length() - 3);
            return !ourNamespacesWithoutIdRefs.contains(ns) && grandParent.getLanguage() != StdLanguages.XML;
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
      else if (ID_ATTR_NAME.equals(name) ||
               NAME_ATTR_NAME.equals(name) ||
               STYLE_ID_ATTR_NAME.equals(name)
              ) {
        final AttributeValueSelfReference attributeValueSelfReference;

        if (jsfNs) {
          attributeValueSelfReference = new AttributeValueSelfReference(element);
        } else {
          attributeValueSelfReference =  new GlobalAttributeValueSelfReference(element);
        }
        return new PsiReference[]{attributeValueSelfReference};
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

  public static class GlobalAttributeValueSelfReference extends AttributeValueSelfReference {

    public GlobalAttributeValueSelfReference(final PsiElement element) {
      super(element);
    }

    public GlobalAttributeValueSelfReference(final PsiElement element, int offset) {
      super(element, offset);
    }

    public GlobalAttributeValueSelfReference(final PsiElement element, TextRange range) {
      super(element, range);
    }

    public boolean isSoft() {
      return false;
    }
  }
}
