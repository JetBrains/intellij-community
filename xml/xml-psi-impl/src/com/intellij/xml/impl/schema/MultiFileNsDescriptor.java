// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  private final List<? extends XmlNSDescriptorImpl> myDescriptors;

  public MultiFileNsDescriptor(List<? extends XmlNSDescriptorImpl> descriptors) {
    myDescriptors = descriptors;
  }

  @Override
  public @Nullable XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag) {
    return getFirst(descriptor -> descriptor.getElementDescriptor(tag));
  }

  @Override
  public XmlElementDescriptor @NotNull [] getRootElementsDescriptors(@Nullable XmlDocument document) {
    return (XmlElementDescriptor[])myDescriptors.stream()
      .flatMap(descriptor -> Arrays.stream(descriptor.getRootElementsDescriptors(document))).toArray();
  }

  @Override
  public @Nullable XmlFile getDescriptorFile() {
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

  @Override
  public Object @NotNull [] getDependencies() {
    return myDescriptors.stream().flatMap(descriptor -> Arrays.stream(descriptor.getDependencies())).toArray();
  }

  @Override
  public @Nullable TypeDescriptor getTypeDescriptor(@NotNull String name, XmlTag context) {
    return getFirst(descriptor -> descriptor.getTypeDescriptor(name, context));
  }

  @Override
  public @Nullable TypeDescriptor getTypeDescriptor(XmlTag descriptorTag) {
    return getFirst(descriptor -> descriptor.getTypeDescriptor(descriptorTag));
  }

  private <T> T getFirst(Function<? super XmlNSDescriptorImpl, ? extends T> function) {
    for (XmlNSDescriptorImpl descriptor : myDescriptors) {
      T t = function.apply(descriptor);
      if (t != null) return t;
    }
    return null;
  }

  @Override
  public boolean processTagsInNamespace(String[] tagNames, PsiElementProcessor<? super XmlTag> processor) {
    for (XmlNSDescriptorImpl descriptor : myDescriptors) {
      if (!descriptor.processTagsInNamespace(tagNames, processor)) return false;
    }
    return true;
  }

  @Override
  public @Nullable XmlElementDescriptor getElementDescriptor(String localName,
                                                             String namespace,
                                                             Set<? super XmlNSDescriptorImpl> visited,
                                                             boolean reference) {
    return getFirst(descriptor -> descriptor.getElementDescriptor(localName, namespace, visited, reference));
  }

  @Override
  public @Nullable XmlAttributeDescriptor getAttribute(String localName, String namespace, XmlTag context) {
    return getFirst(descriptor -> descriptor.getAttribute(localName, namespace, context));
  }

  @Override
  public @Nullable TypeDescriptor findTypeDescriptor(String localName, String namespace) {
    return getFirst(descriptor -> descriptor.findTypeDescriptor(localName, namespace));
  }

  @Override
  public @Nullable XmlTag findGroup(String name) {
    return getFirst(descriptor -> descriptor.findGroup(name));
  }

  @Override
  public @Nullable XmlTag findAttributeGroup(String name) {
    return getFirst(descriptor -> descriptor.findAttributeGroup(name));
  }
}
