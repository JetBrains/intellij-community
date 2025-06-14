// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.handler

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface SpellcheckingElementHandler {

  /**
   * Determines whether the given `psiElement` is eligible for renaming.
   *
   * @param psiElement the PSI element to be checked for renaming eligibility
   * @return `true` if the provided element can be renamed, otherwise `false`
   */
  fun isEligibleForRenaming(psiElement: PsiElement): Boolean

  /**
   * Returns the [PsiNamedElement] associated with the given `psiElement`, if any.
   *
   * @param psiElement the PSI element to get the named element for
   * @return the named element associated with the given `psiElement`, or `null` if no named element is found
   */
  fun getNamedElement(psiElement: PsiElement): PsiNamedElement?
}