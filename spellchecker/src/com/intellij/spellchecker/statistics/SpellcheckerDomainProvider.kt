// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.statistics

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement

interface SpellcheckerDomainProvider {
  fun getDomain(element: PsiElement): String?

  companion object {
    private val EP_NAME = ExtensionPointName.create<SpellcheckerDomainProvider>("com.intellij.spellchecker.domainProvider")

    @JvmStatic
    fun findDomain(element: PsiElement): String? {
      return EP_NAME.extensionList.firstNotNullOfOrNull { it.getDomain(element) }
    }
  }
}
