// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.quickfixes

import com.intellij.codeInsight.intention.EventTrackingIntentionAction
import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.choice.ChoiceTitleIntentionAction
import com.intellij.codeInsight.intention.choice.ChoiceVariantIntentionAction
import com.intellij.codeInsight.intention.choice.DefaultIntentionActionWithChoice
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.startOffset
import com.intellij.spellchecker.statistics.SpellcheckerActionStatistics
import com.intellij.spellchecker.statistics.SpellcheckerRateTracker
import com.intellij.spellchecker.util.SpellCheckerBundle
import com.intellij.util.concurrency.ThreadingAssertions

internal class ChangeTo @JvmOverloads constructor(
  typo: String,
  element: PsiElement,
  private val range: TextRange,
  private val tracker: SpellcheckerRateTracker? = null,
) : DefaultIntentionActionWithChoice, LazySuggestions(typo) {

  private val pointer = SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element, element.containingFile)

  companion object {
    @JvmStatic
    val fixName: String by lazy {
      SpellCheckerBundle.message("change.to.title")
    }
  }

  private object ChangeToTitleAction : ChoiceTitleIntentionAction(fixName, fixName), HighPriorityAction, DumbAware

  override fun getTitle(): ChoiceTitleIntentionAction = ChangeToTitleAction

  private open inner class ChangeToVariantAction(
    override val index: Int,
    private val tracker: SpellcheckerRateTracker? = null,
  ) : ChoiceVariantIntentionAction(), HighPriorityAction, EventTrackingIntentionAction {

    private var suggestion: @NlsSafe String? = null

    override fun getName(): String = suggestion ?: ""

    override fun getTooltipText(): String = SpellCheckerBundle.message("change.to.tooltip", name)

    override fun getFamilyName(): String = fixName

    override fun isShowSubmenu(): Boolean {
      return true
    }

    override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile): Boolean {
      val suggestions = getSuggestions(project)
      if (suggestions.size <= index) return false
      if (getRange(psiFile.viewProvider.document) == null) return false
      suggestion = suggestions[index]
      return true
    }

    override fun applyFix(project: Project, psiFile: PsiFile, editor: Editor?) {
      tracker?.let { SpellcheckerActionStatistics.changeToPerformed(it, index, getSuggestions(project).size) }
      performFix(project, psiFile)
    }

    protected fun performFix(project: Project, psiFile: PsiFile) {
      val suggestion = suggestion ?: return
      val document = psiFile.viewProvider.document
      val myRange = getRange(document) ?: return
      removeHighlightersWithExactRange(document, project, myRange)
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
      return ForPreview()
    }

    override fun startInWriteAction(): Boolean = true

    override fun suggestionShown(project: Project, editor: Editor, psiFile: PsiFile) {
      if (tracker != null && tracker.markShown()) {
        SpellcheckerActionStatistics.suggestionShown(tracker)
      }
    }
  }

  private open inner class ForPreview() : ChangeToVariantAction(-1) {
    override fun applyFix(project: Project, psiFile: PsiFile, editor: Editor?) {
      performFix(project, psiFile)
    }
  }


  override fun getVariants(): List<ChoiceVariantIntentionAction> {
    val limit = Registry.intValue("spellchecker.corrections.limit")
    return (0 until limit).map { ChangeToVariantAction(it, tracker) }
  }

  /**
   * Remove all highlighters with exactly the given range from [DocumentMarkupModel].
   * This might be useful in quick fixes and intention actions to provide immediate feedback.
   * Note that all highlighters at the given range are removed, not only the ones produced by your inspection,
   * but most likely that will look fine:
   * they'll be restored when the new highlighting pass is finished.
   * This method currently works in O(total highlighter count in file) time.
   */
  private fun removeHighlightersWithExactRange(document: Document, project: Project, range: Segment) {
    if (IntentionPreviewUtils.isIntentionPreviewActive()) return
    ThreadingAssertions.assertEventDispatchThread()
    val model = DocumentMarkupModel.forDocument(document, project, false) ?: return

    for (highlighter in model.allHighlighters) {
      if (TextRange.areSegmentsEqual(range, highlighter)) {
        model.removeHighlighter(highlighter)
      }
    }
  }

}