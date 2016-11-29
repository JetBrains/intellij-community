/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.xml.XmlExtension;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.util.XmlUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;

/**
 * @author peter
 */
public class IdReferenceProvider extends PsiReferenceProvider {
  @NonNls public static final String FOR_ATTR_NAME = "for";
  @NonNls public static final String ID_ATTR_NAME = "id";
  @NonNls public static final String STYLE_ID_ATTR_NAME = "styleId";
  @NonNls public static final String NAME_ATTR_NAME = "name";

  private static final THashSet<String> ourNamespacesWithoutNameReference = new THashSet<>();
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
  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
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
