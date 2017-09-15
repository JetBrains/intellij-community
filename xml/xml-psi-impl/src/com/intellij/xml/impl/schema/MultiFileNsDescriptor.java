/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.xml.impl.schema;

import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class MultiFileNsDescriptor implements XsdNsDescriptor {

  private final List<XmlNSDescriptorImpl> myDescriptors;

  public MultiFileNsDescriptor(List<XmlNSDescriptorImpl> descriptors) {
    myDescriptors = descriptors;
  }

  @Nullable
  @Override
  public XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag) {
    return getFirst(descriptor -> descriptor.getElementDescriptor(tag));
  }

  @NotNull
  @Override
  public XmlElementDescriptor[] getRootElementsDescriptors(@Nullable XmlDocument document) {
    return (XmlElementDescriptor[])myDescriptors.stream()
      .flatMap(descriptor -> Arrays.stream(descriptor.getRootElementsDescriptors(document))).toArray();
  }

  @Nullable
  @Override
  public XmlFile getDescriptorFile() {
    return myDescriptors.get(0).getDescriptorFile();
  }

  @Override
  public PsiElement getDeclaration() {
    return null;
  }

  @Override
  public String getName(PsiElement context) {
    return null;
  }

  @Override
  public String getName() {
    return null;
  }

  @Override
  public void init(PsiElement element) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Object[] getDependences() {
    return myDescriptors.stream().flatMap(descriptor -> Arrays.stream(descriptor.getDependences())).toArray();
  }

  @Nullable
  @Override
  public TypeDescriptor getTypeDescriptor(String name, XmlTag context) {
    return getFirst(descriptor -> descriptor.getTypeDescriptor(name, context));
  }

  @Nullable
  @Override
  public TypeDescriptor getTypeDescriptor(XmlTag descriptorTag) {
    return getFirst(descriptor -> descriptor.getTypeDescriptor(descriptorTag));
  }

  private <T> T getFirst(Function<XmlNSDescriptorImpl, T> function) {
    for (XmlNSDescriptorImpl descriptor : myDescriptors) {
      T t = function.apply(descriptor);
      if (t != null) return t;
    }
    return null;
  }

  @Override
  public boolean processTagsInNamespace(String[] tagNames, PsiElementProcessor<XmlTag> processor) {
    for (XmlNSDescriptorImpl descriptor : myDescriptors) {
      if (!descriptor.processTagsInNamespace(tagNames, processor)) return false;
    }
    return true;
  }

  @Nullable
  @Override
  public XmlElementDescriptor getElementDescriptor(String localName,
                                                   String namespace,
                                                   Set<XmlNSDescriptorImpl> visited,
                                                   boolean reference) {
    return getFirst(descriptor -> descriptor.getElementDescriptor(localName, namespace, visited, reference));
  }

  @Nullable
  @Override
  public XmlAttributeDescriptor getAttribute(String localName, String namespace, XmlTag context) {
    return getFirst(descriptor -> descriptor.getAttribute(localName, namespace, context));
  }

  @Nullable
  @Override
  public TypeDescriptor findTypeDescriptor(String localName, String namespace) {
    return getFirst(descriptor -> descriptor.findTypeDescriptor(localName, namespace));
  }

  @Nullable
  @Override
  public XmlTag findGroup(String name) {
    return getFirst(descriptor -> descriptor.findGroup(name));
  }

  @Nullable
  @Override
  public XmlTag findAttributeGroup(String name) {
    return getFirst(descriptor -> descriptor.findAttributeGroup(name));
  }
}
