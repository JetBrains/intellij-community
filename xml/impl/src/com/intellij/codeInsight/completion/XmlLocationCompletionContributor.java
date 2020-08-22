// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.javaee.ExternalResourceManagerEx;
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
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.xml.XmlNamespaceHelper;
import com.intellij.xml.XmlSchemaProvider;
import com.intellij.xml.index.XmlNamespaceIndex;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Dmitry Avdeev
 */
final class XmlLocationCompletionContributor extends CompletionContributor {
  public static final Function<Object, LookupElement> MAPPING = o -> {
    return o instanceof LookupElement ? (LookupElement)o : LookupElementBuilder.create(o);
  };

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
    Set<String> list = new HashSet<>();
    for (XmlSchemaProvider provider : XmlSchemaProvider.EP_NAME.getExtensionList()) {
      if (provider.isAvailable(file)) {
        list.addAll(provider.getAvailableNamespaces(file, null));
      }
    }
    if (!list.isEmpty()) {
      return ArrayUtil.toObjectArray(list);
    }
    Set<String> set = new HashSet<>(ExternalResourceManagerEx.getInstanceEx().getUrlsByNamespace(myElement.getProject()).keySet());
    Set<String> fromIndex =
      XmlNamespaceIndex.getAllResources(null, myElement.getProject()).stream()
        .filter(resource -> "xsd".equals(resource.getFile().getExtension())).map(resource -> resource.getValue().getNamespace())
        .collect(Collectors.toSet());
    ContainerUtil.addAllNotNull(set, fromIndex);
    Object[] resourceUrls = set.toArray();
    final XmlDocument document = file.getDocument();
    assert document != null;
    XmlTag rootTag = document.getRootTag();
    final List<String> additionalNs = new ArrayList<>();
    if (rootTag != null) {
      URLReference.processWsdlSchemas(rootTag, xmlTag -> {
        final String s = xmlTag.getAttributeValue(URLReference.TARGET_NAMESPACE_ATTR_NAME);
        if (s != null) { additionalNs.add(s); }
        return true;
      });
    }
    resourceUrls = ArrayUtil.mergeArrays(resourceUrls, ArrayUtilRt.toStringArray(additionalNs));

    final Set<Object> preferred = new HashSet<>();
    if (parent instanceof XmlAttribute && "xmlns".equals(((XmlAttribute)parent).getName())) {
      XmlNamespaceHelper helper = XmlNamespaceHelper.getHelper(file);
      preferred.addAll(helper.guessUnboundNamespaces(parent.getParent(), file));
    }
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
    }, ArrayUtilRt.EMPTY_OBJECT_ARRAY);
  }
}
