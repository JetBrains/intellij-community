// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class IdReferenceProvider extends PsiReferenceProvider {
  @NonNls public static final String FOR_ATTR_NAME = "for";
  @NonNls public static final String ID_ATTR_NAME = "id";
  @NonNls public static final String STYLE_ID_ATTR_NAME = "styleId";
  @NonNls public static final String NAME_ATTR_NAME = "name";

  private static final Set<String> ourNamespacesWithoutNameReference = new HashSet<>();
  static {
    ourNamespacesWithoutNameReference.add( XmlUtil.JSP_URI );
    ourNamespacesWithoutNameReference.add( XmlUtil.STRUTS_BEAN_URI );
    ourNamespacesWithoutNameReference.add( XmlUtil.STRUTS_BEAN_URI2 );
    ourNamespacesWithoutNameReference.add( XmlUtil.STRUTS_LOGIC_URI );
    Collections.addAll(ourNamespacesWithoutNameReference, XmlUtil.JSTL_CORE_URIS);
    ourNamespacesWithoutNameReference.add( "http://struts.apache.org/tags-tiles" );
    Collections.addAll(ourNamespacesWithoutNameReference, XmlUtil.SCHEMA_URIS);
  }

  public String[] getIdForAttributeNames() {
    return new String[]{FOR_ATTR_NAME, ID_ATTR_NAME, NAME_ATTR_NAME,STYLE_ID_ATTR_NAME};
  }

  public ElementFilter getIdForFilter() {
    return new ElementFilter() {
      @Override
      public boolean isAcceptable(Object element, PsiElement context) {
        final PsiElement grandParent = ((PsiElement)element).getParent().getParent();
        if (grandParent instanceof XmlTag) {
          final XmlTag tag = (XmlTag)grandParent;

          if (!tag.getNamespacePrefix().isEmpty()) {
            return true;
          }
        }
        return false;
      }

      @Override
      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    };
  }

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
    if (element instanceof XmlAttributeValue) {
      final XmlExtension extension = XmlExtension.getExtensionByElement(element);
      if (extension != null && extension.hasDynamicComponents(element)) {
        return PsiReference.EMPTY_ARRAY;
      }

      final PsiElement parentElement = element.getParent();
      if (!(parentElement instanceof XmlAttribute)) return PsiReference.EMPTY_ARRAY;
      final String name = ((XmlAttribute)parentElement).getName();
      final String ns = ((XmlAttribute)parentElement).getParent().getNamespace();
      final boolean jsfNs = Arrays.asList(XmlUtil.JSF_CORE_URIS).contains(ns) ||Arrays.asList(XmlUtil.JSF_HTML_URIS).contains(ns);

      if (FOR_ATTR_NAME.equals(name)) {
        return new PsiReference[]{
          jsfNs && element.getText().indexOf(':') == -1 ?
          new IdRefReference(element):
          new IdRefReference(element) {
            @Override
            public boolean isSoft() {
              final XmlAttributeDescriptor descriptor = ((XmlAttribute)parentElement).getDescriptor();
              return descriptor != null && !descriptor.hasIdRefType();
            }
          }
        };
      }
      else {
        final boolean allowReferences = !ourNamespacesWithoutNameReference.contains(ns);

        if (ID_ATTR_NAME.equals(name) && allowReferences ||
             STYLE_ID_ATTR_NAME.equals(name) ||
             NAME_ATTR_NAME.equals(name) && allowReferences
            ) {
          final AttributeValueSelfReference attributeValueSelfReference;

          if (jsfNs) {
            attributeValueSelfReference = new AttributeValueSelfReference(element);
          } else {
            if (hasOuterLanguageElement(element)) return PsiReference.EMPTY_ARRAY;

            attributeValueSelfReference =  new GlobalAttributeValueSelfReference(element, true);
          }
          return new PsiReference[]{attributeValueSelfReference};
        }
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

  private static boolean hasOuterLanguageElement(@NotNull PsiElement element) {
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof OuterLanguageElement) {
        return true;
      }
    }

    return false;
  }

  public static class GlobalAttributeValueSelfReference extends AttributeValueSelfReference {
    private final boolean mySoft;

    public GlobalAttributeValueSelfReference(PsiElement element, boolean soft) {
      super(element);
      mySoft = soft;
    }

    @Override
    public boolean isSoft() {
      return mySoft;
    }
  }
}
