// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.XmlResolveReferenceSupport
import com.intellij.psi.xml.XmlEntityDecl
import com.intellij.psi.xml.XmlEntityRef

internal object DummyXmlResolveReferenceSupport : XmlResolveReferenceSupport {
  override fun resolveReference(reference: PsiReference?): PsiElement? {
    return null
  }

  override fun resolveReference(ref: XmlEntityRef, targetFile: PsiFile?): XmlEntityDecl? {
    return null
  }

  override fun resolveSchemaTypeOrElementOrAttributeReference(element: PsiElement): PsiElement? {
    return null
  }
}