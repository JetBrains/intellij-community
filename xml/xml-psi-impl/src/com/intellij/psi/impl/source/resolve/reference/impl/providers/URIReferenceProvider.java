/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * @by Maxim.Mossienko
 */
public class URIReferenceProvider extends PsiReferenceProvider {

  public static final ElementFilter ELEMENT_FILTER = new ElementFilter() {
    @Override
    public boolean isAcceptable(Object element, PsiElement context) {
      final PsiElement parent = context.getParent();
      if (parent instanceof XmlAttribute) {
        final XmlAttribute attribute = ((XmlAttribute)parent);
        return attribute.isNamespaceDeclaration();
      }
      return false;
    }

    @Override
    public boolean isClassAcceptable(Class hintClass) {
      return true;
    }
  };
  @NonNls
  private static final String NAMESPACE_ATTR_NAME = "namespace";

  @Override
  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
    final String text = element.getText();
    String s = StringUtil.unquoteString(text);
    final PsiElement parent = element.getParent();

    if (parent instanceof XmlAttribute &&
        XmlUtil.SCHEMA_LOCATION_ATT.equals(((XmlAttribute)parent).getLocalName()) &&
        XmlUtil.XML_SCHEMA_INSTANCE_URI.equals(((XmlAttribute)parent).getNamespace())) {
      final List<PsiReference> refs = new ArrayList<>(2);
      final StringTokenizer tokenizer = new StringTokenizer(s);

      while(tokenizer.hasMoreElements()) {
        final String namespace = tokenizer.nextToken();
        int offset = text.indexOf(namespace);
        TextRange range = new TextRange(offset, offset + namespace.length());
        final URLReference urlReference = new URLReference(element, range, true) {
          @Override
          public boolean isSchemaLocation() {
            return true;
          }
        };
        refs.add(urlReference);
        if (!tokenizer.hasMoreElements()) break;
        String url = tokenizer.nextToken();

        offset = text.indexOf(url);
        refs.add(new DependentNSReference(element, new TextRange(offset,offset + url.length()), urlReference));
        if (!XmlUtil.isUrlText(url, element.getProject())) {
          ContainerUtil.addAll(refs, new FileReferenceSet(url, element, offset, this, false).getAllReferences());
        }
      }

      return refs.toArray(new PsiReference[refs.size()]);
    }

    PsiReference reference = getUrlReference(element, s);
    if (reference != null) return new PsiReference[] { reference };

    s = s.substring(XmlUtil.getPrefixLength(s));
    return new FileReferenceSet(s,element,text.indexOf(s), this,true).getAllReferences();
  }


  static PsiReference getUrlReference(PsiElement element, String s) {
    PsiElement parent = element.getParent();
    if (XmlUtil.isUrlText(s, element.getProject()) ||
        (parent instanceof XmlAttribute &&
          ( ((XmlAttribute)parent).isNamespaceDeclaration() ||
            NAMESPACE_ATTR_NAME.equals(((XmlAttribute)parent).getName())
          )
         )
      ) {
      if (!s.startsWith(XmlUtil.TAG_DIR_NS_PREFIX)) {
        boolean namespaceSoftRef = parent instanceof XmlAttribute &&
          NAMESPACE_ATTR_NAME.equals(((XmlAttribute)parent).getName()) &&
          ((XmlAttribute)parent).getParent().getAttributeValue(XmlUtil.SCHEMA_LOCATION_ATT) != null;
        if (!namespaceSoftRef && parent instanceof XmlAttribute && ((XmlAttribute)parent).isNamespaceDeclaration()) {
          namespaceSoftRef = parent.getContainingFile().getContext() != null;
        }
        return new URLReference(element, null, namespaceSoftRef);
      }
    }
    return null;
  }
}
