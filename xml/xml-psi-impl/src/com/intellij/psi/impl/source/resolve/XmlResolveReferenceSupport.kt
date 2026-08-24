// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.xml.XmlEntityDecl
import com.intellij.psi.xml.XmlEntityRef
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XmlResolveReferenceSupport {
  fun resolveReference(reference: PsiReference?): PsiElement?

  fun resolveReference(ref: XmlEntityRef, targetFile: PsiFile?): XmlEntityDecl?

  fun resolveSchemaTypeOrElementOrAttributeReference(element: PsiElement): PsiElement?
}
