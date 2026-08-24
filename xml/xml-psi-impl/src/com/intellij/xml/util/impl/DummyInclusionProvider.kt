// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.util.InclusionProvider

internal object DummyInclusionProvider : InclusionProvider {
  override fun getIncludedTags(xincludeTag: XmlTag): Array<PsiElement> = emptyArray()

  override fun shouldProcessIncludesNow(): Boolean = true
}