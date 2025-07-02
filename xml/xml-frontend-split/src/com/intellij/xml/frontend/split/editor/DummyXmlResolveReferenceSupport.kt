// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.frontend.split.editor

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.XmlResolveReferenceSupport
import com.intellij.psi.xml.XmlEntityDecl

class DummyXmlResolveReferenceSupport : XmlResolveReferenceSupport {
  override fun resolveReference(reference: PsiReference?): PsiElement? {
    return null
  }

  override fun resolveReference(ref: com.intellij.psi.xml.XmlEntityRef, targetFile: PsiFile?): XmlEntityDecl? {
    return null
  }

  override fun resolveSchemaTypeOrElementOrAttributeReference(element: PsiElement): PsiElement? {
    return null
  }
}