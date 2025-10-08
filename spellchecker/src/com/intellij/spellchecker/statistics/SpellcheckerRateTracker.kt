// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.statistics

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.psi.PsiElement
import com.intellij.spellchecker.inspections.SpellCheckingInspection.SpellCheckingScope
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy.getSpellcheckingStrategy
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class SpellcheckerRateTracker private constructor(
  val project: Project,
  val language: Language,
  val domain: String,
) {
  constructor(element: PsiElement) : this(
    element.project,
    element.language,
    determineDomain(element)
  )

  private val shown: AtomicBoolean = AtomicBoolean(false)
  fun markShown(): Boolean {
    return shown.compareAndSet(false, true)
  }


  companion object {
    private fun determineDomain(element: PsiElement): String {
      val strategy = getSpellcheckingStrategy(element)
      if (CommitMessage.isCommitMessage(element)) {
        return "commit"
      }
      else if (strategy.elementFitsScope(element, setOf(SpellCheckingScope.Literals))) {
        return "literal"
      }
      else if (strategy.elementFitsScope(element, setOf(SpellCheckingScope.Comments))) {
        return "comment"
      }
      else {
        return "code"
      }
    }
  }
}
