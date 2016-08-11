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
package com.intellij.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.SchemaPrefix;
import com.intellij.psi.impl.source.xml.TagNameVariantCollector;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public class DefaultXmlExtension extends XmlExtension {
  public static final XmlExtension DEFAULT_EXTENSION = new DefaultXmlExtension();

  @Override
  public boolean isAvailable(final PsiFile file) {
    return true;
  }

  @Override
  @NotNull
  public List<TagInfo> getAvailableTagNames(@NotNull final XmlFile file, @NotNull final XmlTag context) {

    final Set<String> namespaces = new HashSet<>(Arrays.asList(context.knownNamespaces()));
    final List<XmlSchemaProvider> providers = XmlSchemaProvider.getAvailableProviders(file);
    for (XmlSchemaProvider provider : providers) {
      namespaces.addAll(provider.getAvailableNamespaces(file, null));
    }
    List<String> nsInfo = new ArrayList<>();
    List<XmlElementDescriptor> descriptors = TagNameVariantCollector.getTagDescriptors(context, namespaces, nsInfo);
    final List<TagInfo> set = new ArrayList<>();
    for (int i = 0; i < descriptors.size(); i++) {
      final XmlElementDescriptor descriptor = descriptors.get(i);
      String qualifiedName = descriptor.getName(context);
      LOG.assertTrue(qualifiedName != null, descriptor + " returned null name");
      final int pos = qualifiedName.indexOf(':');
      final String name = pos >= 0 ? qualifiedName.substring(pos + 1) : qualifiedName;
      set.add(new TagInfo(name, nsInfo.get(i)) {
        @Nullable
        @Override
        public PsiElement getDeclaration() {
          return descriptor.getDeclaration();
        }
      });
    }
    return set;
  }

  public static Set<String> filterNamespaces(final Set<String> namespaces, final String tagName, final XmlFile context) {
    if (tagName == null) {
      return namespaces;
    }
    final HashSet<String> set = new HashSet<>();
    for (String namespace : namespaces) {
      final XmlFile xmlFile = XmlUtil.findNamespace(context, namespace);
      if (xmlFile != null) {
        final XmlDocument document = xmlFile.getDocument();
        assert document != null;
        final XmlNSDescriptor nsDescriptor = (XmlNSDescriptor)document.getMetaData();
        assert nsDescriptor != null;
        final XmlElementDescriptor[] elementDescriptors = nsDescriptor.getRootElementsDescriptors(document);
        for (XmlElementDescriptor elementDescriptor : elementDescriptors) {
          LOG.assertTrue(elementDescriptor != null, "Null returned from " + nsDescriptor);
          if (hasTag(elementDescriptor, tagName, new HashSet<>())) {
            set.add(namespace);
            break;
          }
        }
      }
    }
    return set;
  }

  private static boolean hasTag(@NotNull XmlElementDescriptor elementDescriptor, String tagName, Set<XmlElementDescriptor> visited) {
    final String name = elementDescriptor.getDefaultName();
    if (name == null) {
      LOG.error(elementDescriptor + " returned null as default name");
      return false;
    }
    if (name.equals(tagName)) {
      return true;
    }
    for (XmlElementDescriptor descriptor : elementDescriptor.getElementsDescriptors(null)) {
      if (!visited.contains(elementDescriptor)) {
        visited.add(elementDescriptor);
        if (hasTag(descriptor, tagName, visited)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public SchemaPrefix getPrefixDeclaration(final XmlTag context, String namespacePrefix) {
    @NonNls String nsDeclarationAttrName = null;
    for(XmlTag t = context; t != null; t = t.getParentTag()) {
      if (t.hasNamespaceDeclarations()) {
        if (nsDeclarationAttrName == null) nsDeclarationAttrName = namespacePrefix.length() > 0 ? "xmlns:"+namespacePrefix:"xmlns";
        XmlAttribute attribute = t.getAttribute(nsDeclarationAttrName);
        if (attribute != null) {
          final String attrPrefix = attribute.getNamespacePrefix();
          final TextRange textRange = TextRange.from(attrPrefix.length() + 1, namespacePrefix.length());
          return new SchemaPrefix(attribute, textRange, namespacePrefix);
        }
      }
    }
    return null;
  }

  private final static Logger LOG = Logger.getInstance(DefaultXmlExtension.class);

}
