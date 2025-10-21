// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * @author Dmitry Avdeev
 */
public class XmlBaseReferenceProvider extends PsiReferenceProvider {

  private final boolean myAcceptSelf;

  public XmlBaseReferenceProvider(boolean acceptSelf) {
    myAcceptSelf = acceptSelf;
  }

  @Override
  public PsiReference @NotNull [] getReferencesByElement(final @NotNull PsiElement element, @NotNull ProcessingContext context) {
    PsiReference reference = URIReferenceProvider.getUrlReference(element, ElementManipulators.getValueText(element));
    if (reference != null) return new PsiReference[] { reference };
    FileReferenceSet referenceSet = FileReferenceSet.createSet(element, false, false, false);
    referenceSet.addCustomization(FileReferenceSet.DEFAULT_PATH_EVALUATOR_OPTION, file -> getContext(element, file));
    return referenceSet.getAllReferences();
  }

  private @Unmodifiable Collection<PsiFileSystemItem> getContext(PsiElement element, PsiFile file) {
    XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    if (!myAcceptSelf && tag != null) {
      tag = tag.getParentTag();
    }
    while (tag != null) {
      XmlAttribute base = tag.getAttribute("base", XmlUtil.XML_NAMESPACE_URI);
      if (base != null) {
        XmlAttributeValue value = base.getValueElement();
        if (value != null) {
          PsiReference reference = value.getReference();
          if (reference instanceof PsiPolyVariantReference) {
            ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(false);
            return ContainerUtil.map(results, result -> (PsiFileSystemItem)result.getElement());
          }
        }
      }
      tag = tag.getParentTag();
    }
    PsiDirectory directory = file.getContainingDirectory();
    return ContainerUtil.createMaybeSingletonList(directory);
  }
}
