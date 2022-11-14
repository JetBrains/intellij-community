// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.quickfixes

import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.choice.ChoiceTitleIntentionAction
import com.intellij.codeInsight.intention.choice.ChoiceVariantIntentionAction
import com.intellij.codeInsight.intention.choice.DefaultIntentionActionWithChoice
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.refactoring.suggested.startOffset
import com.intellij.spellchecker.util.SpellCheckerBundle

class ChangeTo(typo: String, element: PsiElement, private val range: TextRange) : DefaultIntentionActionWithChoice, LazySuggestions(typo) {
  private val pointer = SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element, element.containingFile)

  companion object {
    @JvmStatic
    val fixName: String by lazy {
      SpellCheckerBundle.message("change.to.title")
    }
  }

  private object ChangeToTitleAction : ChoiceTitleIntentionAction(fixName, fixName), HighPriorityAction

  override fun getTitle(): ChoiceTitleIntentionAction = ChangeToTitleAction


  private inner class ChangeToVariantAction(
    override val index: Int
  ) : ChoiceVariantIntentionAction(), HighPriorityAction {

    @NlsSafe
    private var suggestion: String? = null

    override fun getName(): String = suggestion ?: ""

    override fun getTooltipText(): String = SpellCheckerBundle.message("change.to.tooltip", suggestion)

    override fun getFamilyName(): String = fixName

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
      val suggestions = getSuggestions(project)
      if (suggestions.size <= index) return false
      if (getRange(file.viewProvider.document) == null) return false
      suggestion = suggestions[index]
      return true
    }

    override fun applyFix(project: Project, file: PsiFile, editor: Editor?) {
      val suggestion = suggestion ?: return

      val document = file.viewProvider.document
      val myRange = getRange(document) ?: return

      UpdateHighlightersUtil.removeHighlightersWithExactRange(document, project, myRange)

      document.replaceString(myRange.startOffset, myRange.endOffset, suggestion)
    }
    
    private fun getRange(document: Document): TextRange? {
      val element = pointer.element ?: return null
      val range = range.shiftRight(element.startOffset)
      if (range.startOffset < 0 || range.endOffset > document.textLength) return null

      val text = document.getText(range)
      if (text != typo) return null
      return range
    }

    override fun getFileModifierForPreview(target: PsiFile): FileModifier {
      return this
    }

    override fun startInWriteAction(): Boolean = true
  }


  override fun getVariants(): List<ChoiceVariantIntentionAction> {
    val limit = Registry.intValue("spellchecker.corrections.limit")

    return (0 until limit).map { ChangeToVariantAction(it) }
  }
}