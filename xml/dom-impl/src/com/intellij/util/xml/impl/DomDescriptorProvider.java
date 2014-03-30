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
package com.intellij.util.xml.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.PsiElement;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.dom.DomElementXmlDescriptor;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DefinesXml;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class DomDescriptorProvider implements XmlElementDescriptorProvider {

  @Nullable
  public XmlElementDescriptor getDescriptor(final XmlTag tag) {
    Project project = tag.getProject();
    if (project.isDefault()) return null;
    final DomElement domElement = DomManager.getDomManager(project).getDomElement(tag);
    if (domElement != null) {
      final DefinesXml definesXml = domElement.getAnnotation(DefinesXml.class);
      if (definesXml != null) {
        return new DomElementXmlDescriptor(domElement);
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
