// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DefinesXml;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.dom.DomElementXmlDescriptor;
import org.jetbrains.annotations.Nullable;


public class DomDescriptorProvider implements XmlElementDescriptorProvider {

  @Override
  public @Nullable XmlElementDescriptor getDescriptor(final XmlTag tag) {
    Project project = tag.getProject();
    if (project.isDefault()) return null;
    
    final DomInvocationHandler handler = DomManagerImpl.getDomManager(project).getDomHandler(tag);
    if (handler != null) {
      final DefinesXml definesXml = handler.getAnnotation(DefinesXml.class);
      if (definesXml != null) {
        return new DomElementXmlDescriptor(handler);
      }
      final PsiElement parent = tag.getParent();
      if (parent instanceof XmlTag) {
        final XmlElementDescriptor descriptor = ((XmlTag)parent).getDescriptor();

        if (descriptor instanceof DomElementXmlDescriptor) {
          return descriptor.getElementDescriptor(tag, (XmlTag)parent);
        }
      }
    }

    return null;
  }
}
