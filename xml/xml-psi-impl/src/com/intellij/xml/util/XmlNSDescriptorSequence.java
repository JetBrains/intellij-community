// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class XmlNSDescriptorSequence implements XmlNSDescriptor{
  final List<XmlNSDescriptor> sequence = new ArrayList<>();

  public XmlNSDescriptorSequence(){
  }

  public XmlNSDescriptorSequence(XmlNSDescriptor[] descriptors){
    for (final XmlNSDescriptor descriptor : descriptors) {
      add(descriptor);
    }
  }

  public void add(XmlNSDescriptor descriptor){
    sequence.add(descriptor);
  }

  @Override
  public XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag){
    for (XmlNSDescriptor descriptor : sequence) {
      final XmlElementDescriptor elementDescriptor = descriptor.getElementDescriptor(tag);
      if (elementDescriptor != null) return elementDescriptor;
    }
    return null;
  }

  @Override
  public XmlElementDescriptor @NotNull [] getRootElementsDescriptors(final @Nullable XmlDocument document) {
    final List<XmlElementDescriptor> descriptors = new ArrayList<>();
    for (XmlNSDescriptor descriptor : sequence) {
      ContainerUtil.addAll(descriptors, descriptor.getRootElementsDescriptors(document));
    }

    return descriptors.toArray(XmlElementDescriptor.EMPTY_ARRAY);
  }

  @Override
  public XmlFile getDescriptorFile(){
    for (XmlNSDescriptor descriptor : sequence) {
      final XmlFile file = descriptor.getDescriptorFile();
      if (file != null) return file;
    }
    return null;
  }

  public List<XmlNSDescriptor> getSequence(){
    return sequence;
  }

  @Override
  public PsiElement getDeclaration(){
    for (XmlNSDescriptor descriptor : sequence) {
      final PsiElement declaration = descriptor.getDeclaration();
      if (declaration != null) return declaration;
    }
    return null;
  }

  @Override
  public String getName(PsiElement context){
    for (XmlNSDescriptor descriptor : sequence) {
      final String name = descriptor.getName(context);
      if (name != null) return name;
    }
    return null;
  }

  @Override
  public String getName(){
    for (XmlNSDescriptor descriptor : sequence) {
      final String name = descriptor.getName();
      if (name != null) return name;
    }
    return null;
  }

  @Override
  public void init(PsiElement element){
    for (XmlNSDescriptor descriptor : sequence) {
      descriptor.init(element);
    }
  }

  @Override
  public Object @NotNull [] getDependencies(){
    List<Object> ret = new ArrayList<>();
    for (XmlNSDescriptor descriptor : sequence) {
      ContainerUtil.addAll(ret, descriptor.getDependencies());
    }
    return ret.toArray();
  }
}
