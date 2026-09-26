// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util

import com.intellij.openapi.components.serviceOrNull
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.util.impl.DummyInclusionProvider

interface InclusionProvider {
  fun getIncludedTags(xincludeTag: XmlTag): Array<PsiElement>

  fun shouldProcessIncludesNow(): Boolean

  companion object {
    @JvmStatic
    fun getInstance(): InclusionProvider {
      return serviceOrNull<InclusionProvider>() ?: DummyInclusionProvider
    }
  }
}