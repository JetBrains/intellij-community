// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.inspections


import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

@ApiStatus.Experimental
@ApiStatus.Internal
interface SpellcheckingExtension {

  /**
   * Performs spell-checking on the specified PSI element.
   *
   * The implementation may examine neighboring elements of [PsiElement] if needed.
   * In case of doing that, it's implementation's responsibility to not check the same element for spelling mistake twice.
   *
   * @param element The PSI element to check for spelling errors
   * @param consumer The callback function that will be invoked for each spelling error detected during the inspection
   *
   * @return [SpellCheckingResult.Checked] if the PSI element has been checked, [SpellCheckingResult.Ignored] otherwise
   */
  fun spellcheck(element: PsiElement, session: LocalInspectionToolSession, consumer: Consumer<SpellingTypo>): SpellCheckingResult

  companion object {
    private val EP_NAME = ExtensionPointName<SpellcheckingExtension>("com.intellij.spellchecker.extension")

    fun spellcheck(element: PsiElement, session: LocalInspectionToolSession, consumer: Consumer<SpellingTypo>): SpellCheckingResult =
      EP_NAME.extensionList.asSequence()
        .map { it.spellcheck(element, session, consumer) }
        .firstOrNull { it == SpellCheckingResult.Checked } ?: SpellCheckingResult.Ignored
  }

  /** A typo detected by [SpellcheckingExtension] in a sentence or text inside a [PsiElement]. */
  interface SpellingTypo {
    /** The misspelled word inside the [element] */
    val word: String

    /** The range of the typo in the [element]'s text */
    val range: TextRange

    /** Element that contains a misspelled [word] within the given text [range] */
    val element: PsiElement
  }

  enum class SpellCheckingResult {
    Checked, Ignored
  }
}