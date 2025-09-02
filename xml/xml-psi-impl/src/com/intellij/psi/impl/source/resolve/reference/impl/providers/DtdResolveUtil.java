// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;


public final class DtdResolveUtil {
  @ApiStatus.Internal
  public static @Nullable XmlNSDescriptor getNsDescriptor(XmlElement element) {
    final XmlElement parentThatProvidesMetaData = PsiTreeUtil
      .getParentOfType(CompletionUtilCoreImpl.getOriginalElement(element), XmlDocument.class, XmlMarkupDecl.class);

    if (parentThatProvidesMetaData instanceof XmlDocument document) {
      XmlNSDescriptor rootTagNSDescriptor = document.getRootTagNSDescriptor();
      if (rootTagNSDescriptor == null) rootTagNSDescriptor = (XmlNSDescriptor)document.getMetaData();
      return rootTagNSDescriptor;
    } else if (parentThatProvidesMetaData instanceof XmlMarkupDecl markupDecl) {
      final PsiMetaData psiMetaData = markupDecl.getMetaData();

      if (psiMetaData instanceof XmlNSDescriptor) {
        return (XmlNSDescriptor)psiMetaData;
      }
    }

    return null;
  }

  public static @Nullable XmlElementDescriptor resolveElementReference(String name, XmlElement context) {
    XmlNSDescriptor rootTagNSDescriptor = getNsDescriptor(context);

    if (rootTagNSDescriptor instanceof XmlNSDescriptorImpl) {
      return ((XmlNSDescriptorImpl)rootTagNSDescriptor).getElementDescriptor(name);
    }
    return null;
  }
}
