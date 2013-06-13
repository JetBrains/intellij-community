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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.completion.CompletionUtilCoreImpl;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlMarkupDecl;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.dtd.XmlNSDescriptorImpl;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class DtdResolveUtil {
  @Nullable
  static XmlNSDescriptor getNsDescriptor(XmlElement element) {
    final XmlElement parentThatProvidesMetaData = PsiTreeUtil
      .getParentOfType(CompletionUtilCoreImpl.getOriginalElement(element), XmlDocument.class, XmlMarkupDecl.class);

    if (parentThatProvidesMetaData instanceof XmlDocument) {
      final XmlDocument document = (XmlDocument)parentThatProvidesMetaData;
      XmlNSDescriptor rootTagNSDescriptor = document.getRootTagNSDescriptor();
      if (rootTagNSDescriptor == null) rootTagNSDescriptor = (XmlNSDescriptor)document.getMetaData();
      return rootTagNSDescriptor;
    } else if (parentThatProvidesMetaData instanceof XmlMarkupDecl) {
      final XmlMarkupDecl markupDecl = (XmlMarkupDecl)parentThatProvidesMetaData;
      final PsiMetaData psiMetaData = markupDecl.getMetaData();

      if (psiMetaData instanceof XmlNSDescriptor) {
        return (XmlNSDescriptor)psiMetaData;
      }
    }

    return null;
  }

  @Nullable
  public static XmlElementDescriptor resolveElementReference(String name, XmlElement context) {
    XmlNSDescriptor rootTagNSDescriptor = getNsDescriptor(context);

    if (rootTagNSDescriptor instanceof XmlNSDescriptorImpl) {
      return ((XmlNSDescriptorImpl)rootTagNSDescriptor).getElementDescriptor(name);
    }
    return null;
  }
}
