// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlElementDecl;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;


public class XmlReadWriteAccessDetector extends ReadWriteAccessDetector {
  @Override
  public boolean isReadWriteAccessible(final @NotNull PsiElement element) {
    return element instanceof XmlAttributeValue ||
        element instanceof XmlTag ||
        element instanceof XmlElementDecl ||
        element instanceof XmlComment; // e.g. <!--@elvariable name="xxx" type="yyy"-->
  }

  @Override
  public boolean isDeclarationWriteAccess(final @NotNull PsiElement element) {
    return false;
  }

  @Override
  public @NotNull Access getReferenceAccess(final @NotNull PsiElement referencedElement, final @NotNull PsiReference reference) {
    PsiElement refElement = reference.getElement();
    return refElement instanceof XmlAttributeValue &&
           (!(referencedElement instanceof XmlTag) || refElement.getParent().getParent() == referencedElement) ||
            refElement instanceof XmlElementDecl ||
            refElement instanceof XmlComment   // e.g. <!--@elvariable name="xxx" type="yyy"-->
           ? Access.Write : Access.Read;

  }

  @Override
  public @NotNull Access getExpressionAccess(final @NotNull PsiElement expression) {
    return expression instanceof XmlAttributeValue ? Access.Write : Access.Read;
  }
}
