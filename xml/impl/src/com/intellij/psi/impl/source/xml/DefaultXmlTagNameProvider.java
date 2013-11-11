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
package com.intellij.psi.impl.source.xml;

import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.completion.XmlTagInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.XmlTagNameProvider;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DefaultXmlTagNameProvider implements XmlTagNameProvider {
  @Override
  public void addTagNameVariants(List<LookupElement> elements, @NotNull XmlTag tag, String prefix) {
    final List<String> namespaces;
    if (prefix.isEmpty()) {
      namespaces = new ArrayList<String>(Arrays.asList(tag.knownNamespaces()));
      namespaces.add(XmlUtil.EMPTY_URI); // empty namespace
    }
    else {
      namespaces = new ArrayList<String>(Collections.singletonList(tag.getNamespace()));
    }
    XmlExtension xmlExtension = XmlExtension.getExtension(tag.getContainingFile());
    List<String> nsInfo = new ArrayList<String>();
    @SuppressWarnings("unchecked") List<XmlElementDescriptor> variants = TagNameVariantCollector
      .getTagDescriptors(tag, namespaces, nsInfo);

    for (int i = 0; i < variants.size(); i++) {
      XmlElementDescriptor descriptor = variants.get(i);
      String qname = descriptor.getName(tag);
      if (!prefix.isEmpty() && qname.startsWith(prefix + ":")) {
        qname = qname.substring(prefix.length() + 1);
      }
      PsiElement declaration = descriptor.getDeclaration();
      LookupElementBuilder lookupElement = declaration == null ? LookupElementBuilder.create(qname) : LookupElementBuilder.create(declaration, qname);
      final int separator = qname.indexOf(':');
      if (separator > 0) {
        lookupElement = lookupElement.withLookupString(qname.substring(separator + 1));
      }
      String ns = nsInfo.get(i);
      if (StringUtil.isNotEmpty(ns)) {
        lookupElement = lookupElement.withTypeText(ns, true);
      }
      if (descriptor instanceof PsiPresentableMetaData) {
        lookupElement = lookupElement.withIcon(((PsiPresentableMetaData)descriptor).getIcon());
      }
      if (xmlExtension.useXmlTagInsertHandler()) {
        lookupElement = lookupElement.withInsertHandler(XmlTagInsertHandler.INSTANCE);
      }

      elements.add(PrioritizedLookupElement.withPriority(lookupElement, separator > 0 ? 0 : 1));
    }
  }

}
