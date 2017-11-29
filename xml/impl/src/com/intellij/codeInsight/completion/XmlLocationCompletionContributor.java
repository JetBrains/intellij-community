/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.DependentNSReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.URLReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.xml.XmlNamespaceHelper;
import com.intellij.xml.XmlSchemaProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class XmlLocationCompletionContributor extends CompletionContributor {

  public static final Function<Object, LookupElement> MAPPING =
    o -> o instanceof LookupElement ? (LookupElement)o : LookupElementBuilder.create(o);

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    PsiReference reference = parameters.getPosition().getContainingFile().findReferenceAt(parameters.getOffset());
    if (reference instanceof URLReference) {
      if (((URLReference)reference).isSchemaLocation()) {
        Object[] objects = completeSchemaLocation(reference.getElement());
        result.addAllElements(ContainerUtil.map(objects, MAPPING));
        if (objects.length > 0) result.stopHere();
        return;
      }
      Object[] objects = completeNamespace(reference.getElement());
      result.addAllElements(ContainerUtil.map(objects, MAPPING));
      if (objects.length > 0) result.stopHere();
      return;
    }
    if (reference instanceof PsiMultiReference) reference = ((PsiMultiReference)reference).getReferences()[0];
    if (reference instanceof DependentNSReference) {
      MultiMap<String, String> map = ExternalResourceManagerEx.getInstanceEx().getUrlsByNamespace(parameters.getOriginalFile().getProject());
      String namespace = ((DependentNSReference)reference).getNamespaceReference().getCanonicalText();
      Collection<String> strings = map.get(namespace);
      for (String string : strings) {
        if (!namespace.equals(string)) { // exclude namespaces from location urls
          result.consume(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(string), 100));
        }
      }
      if (!strings.isEmpty()) result.stopHere();
    }
  }

  private static Object[] completeNamespace(PsiElement myElement) {
    final XmlFile file = (XmlFile)myElement.getContainingFile();
    PsiElement parent = myElement.getParent();
    final Set<Object> preferred = new HashSet<>();
    if (parent instanceof XmlAttribute && "xmlns".equals(((XmlAttribute)parent).getName())) {
      XmlNamespaceHelper helper = XmlNamespaceHelper.getHelper(file);
      preferred.addAll(helper.guessUnboundNamespaces(parent.getParent(), file));
    }
    Set<String> list = new HashSet<>();
    for (XmlSchemaProvider provider : Extensions.getExtensions(XmlSchemaProvider.EP_NAME)) {
      if (provider.isAvailable(file)) {
        list.addAll(provider.getAvailableNamespaces(file, null));
      }
    }
    if (!list.isEmpty()) {
      return ArrayUtil.toObjectArray(list);
    }
    Object[] resourceUrls = ExternalResourceManagerEx.getInstanceEx().getUrlsByNamespace(myElement.getProject()).keySet().toArray();
    final XmlDocument document = file.getDocument();
    assert document != null;
    XmlTag rootTag = document.getRootTag();
    final ArrayList<String> additionalNs = new ArrayList<>();
    if (rootTag != null) URLReference.processWsdlSchemas(rootTag, xmlTag -> {
      final String s = xmlTag.getAttributeValue(URLReference.TARGET_NAMESPACE_ATTR_NAME);
      if (s != null) { additionalNs.add(s); }
      return true;
    });
    resourceUrls = ArrayUtil.mergeArrays(resourceUrls, ArrayUtil.toStringArray(additionalNs));

    return ContainerUtil.map2Array(resourceUrls, o -> {
      LookupElementBuilder builder = LookupElementBuilder.create(o);
      return preferred.contains(o) ? PrioritizedLookupElement.withPriority(builder, 100) : builder;
    });
  }

  private static Object[] completeSchemaLocation(PsiElement element) {
    XmlTag tag = (XmlTag)element.getParent().getParent();
    XmlAttribute[] attributes = tag.getAttributes();
    final PsiReference[] refs = element.getReferences();
    return ContainerUtil.mapNotNull(attributes, attribute -> {
      final String attributeValue = attribute.getValue();
      return attributeValue != null &&
             attribute.isNamespaceDeclaration() &&
             ContainerUtil.find(refs, ref -> ref.getCanonicalText().equals(attributeValue)) == null ? attributeValue + " " : null;
    }, ArrayUtil.EMPTY_OBJECT_ARRAY);
  }
}
