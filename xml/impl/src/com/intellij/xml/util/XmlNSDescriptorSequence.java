/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 08.09.2003
 * Time: 17:27:43
 * To change this template use Options | File Templates.
 */
public class XmlNSDescriptorSequence implements XmlNSDescriptor{
  final List<XmlNSDescriptor> sequence = new ArrayList<XmlNSDescriptor>();

  public XmlNSDescriptorSequence(){
  }

  public XmlNSDescriptorSequence(XmlNSDescriptor[] descriptors){
    for(int i = 0; i < descriptors.length; i++){
      final XmlNSDescriptor descriptor = descriptors[i];
      add(descriptor);
    }
  }

  public void add(XmlNSDescriptor descriptor){
    sequence.add(descriptor);
  }

  public XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag){
    final Iterator iterator = sequence.iterator();
    while(iterator.hasNext()){
      final XmlNSDescriptor descriptor = (XmlNSDescriptor) iterator.next();
      final XmlElementDescriptor elementDescriptor = descriptor.getElementDescriptor(tag);
      if(elementDescriptor != null) return elementDescriptor;
    }
    return null;
  }

  @NotNull
  public XmlElementDescriptor[] getRootElementsDescriptors(@Nullable final XmlDocument document) {
    final List<XmlElementDescriptor> descriptors = new ArrayList<XmlElementDescriptor>();
    final Iterator iterator = sequence.iterator();
    while(iterator.hasNext()) {
      final XmlNSDescriptor descriptor = (XmlNSDescriptor)iterator.next();
      ContainerUtil.addAll(descriptors, descriptor.getRootElementsDescriptors(document));
    }

    return descriptors.toArray(new XmlElementDescriptor[descriptors.size()]);
  }

  public XmlFile getDescriptorFile(){
    final Iterator iterator = sequence.iterator();
    while(iterator.hasNext()){
      final XmlNSDescriptor descriptor = (XmlNSDescriptor) iterator.next();
      final XmlFile file = descriptor.getDescriptorFile();
      if(file != null) return file;
    }
    return null;
  }

  public List<XmlNSDescriptor> getSequence(){
    return sequence;
  }

  public boolean isHierarhyEnabled() {
    final Iterator iterator = sequence.iterator();
    while(iterator.hasNext()){
      final XmlNSDescriptor descriptor = (XmlNSDescriptor) iterator.next();
      if(descriptor.isHierarhyEnabled()) return true;
    }
    return false;
  }

  public PsiElement getDeclaration(){
    final Iterator iterator = sequence.iterator();
    while(iterator.hasNext()){
      final XmlNSDescriptor descriptor = (XmlNSDescriptor) iterator.next();
      final PsiElement declaration = descriptor.getDeclaration();
      if(declaration != null) return declaration;
    }
    return null;
  }

  public String getName(PsiElement context){
    final Iterator iterator = sequence.iterator();
    while(iterator.hasNext()){
      final XmlNSDescriptor descriptor = (XmlNSDescriptor) iterator.next();
      final String name = descriptor.getName(context);
      if(name != null) return name;
    }
    return null;
  }

  public String getName(){
    final Iterator iterator = sequence.iterator();
    while(iterator.hasNext()){
      final XmlNSDescriptor descriptor = (XmlNSDescriptor) iterator.next();
      final String name = descriptor.getName();
      if(name != null) return name;
    }
    return null;
  }

  public void init(PsiElement element){
    final Iterator iterator = sequence.iterator();
    while(iterator.hasNext()){
      final XmlNSDescriptor descriptor = (XmlNSDescriptor) iterator.next();
      descriptor.init(element);
    }
  }

  public Object[] getDependences(){
    final List<Object> ret = new ArrayList<Object>();
    final Iterator iterator = sequence.iterator();
    while(iterator.hasNext()) {
      final XmlNSDescriptor descriptor = (XmlNSDescriptor)iterator.next();
      ContainerUtil.addAll(ret, descriptor.getDependences());
    }
    return ret.toArray();
  }
}
