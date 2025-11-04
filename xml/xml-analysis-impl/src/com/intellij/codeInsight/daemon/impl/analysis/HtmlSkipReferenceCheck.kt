// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiReference

interface HtmlSkipReferenceCheck {
  fun skipCheck(reference: PsiReference): Boolean

  companion object {
    @JvmField
    val EP_NAME : ExtensionPointName<HtmlSkipReferenceCheck> = ExtensionPointName.create("com.intellij.html.skipReferenceCheck")

    @JvmStatic
    fun isSkip(reference: PsiReference): Boolean {
      return EP_NAME.extensionList.any { it.skipCheck(reference) }
    }
  }
}