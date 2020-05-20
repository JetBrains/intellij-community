// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.quickfixes

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.ide.DataManager
import com.intellij.injected.editor.EditorWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.Anchor
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.spellchecker.SpellCheckerManager
import com.intellij.spellchecker.util.SpellCheckerBundle
import icons.SpellcheckerIcons
import javax.swing.Icon


/**
 * Quick fix for spellchecker that will not call LookupManager, if there is only one suggestion.
 * Instead it will add suggestion to name and replacement will be performed right after choice
 * of quick-fix entry.
 *
 *
 * Note, that during [getName] suggestion will be calculated.
 * Thus, fix should **never** be used in a batch mode
 */
class ChangeToWithSingleFix(val typo: String, val project: Project) : SpellCheckerQuickFix {
  private val suggestions by lazy {
    SpellCheckerManager.getInstance(project).getSuggestions(typo)
  }

  private val isSingleFix by lazy {
    suggestions.size == 1 && !ApplicationManager.getApplication().isUnitTestMode
  }

  override fun getIcon(flags: Int): Icon {
    return SpellcheckerIcons.Spellcheck
  }

  override fun getFamilyName(): String {
    return fixName
  }

  override fun getName(): String {
    return if (isSingleFix) {
      SpellCheckerBundle.message("change.to.0", suggestions.first());
    }
    else {
      familyName;
    }
  }

  override fun getPopupActionAnchor(): Anchor {
    return Anchor.FIRST
  }

  override fun startInWriteAction(): Boolean {
    return isSingleFix
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement ?: return

    DataManager.getInstance()
      .dataContextFromFocusAsync
      .onSuccess { context: DataContext ->
        var editor: Editor = CommonDataKeys.EDITOR.getData(context) ?: return@onSuccess

        if (InjectedLanguageManager.getInstance(project).getInjectionHost(element) != null && editor !is EditorWindow) {
          editor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(editor, element.containingFile)
        }

        val textRange = (descriptor as ProblemDescriptorBase).textRange ?: return@onSuccess

        val documentLength: Int = editor.document.textLength

        val endOffset = getDocumentOffset(textRange.endOffset, documentLength)
        val startOffset = getDocumentOffset(textRange.startOffset, documentLength)
        val word: String = editor.document.getText(TextRange.create(startOffset, endOffset))

        if (word.isBlank() || word != typo) {
          return@onSuccess
        }

        if (isSingleFix) {
          val suggestion = suggestions.single()

          with(editor) {
            document.replaceString(startOffset, endOffset, suggestion)
            caretModel.moveToOffset(textRange.startOffset + suggestion.length)
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            scrollingModel.scrollToCaret(ScrollType.RELATIVE)
          }
        }
        else {
          editor.selectionModel.setSelection(startOffset, endOffset)
          val items: Array<LookupElement> = suggestions.map { LookupElementBuilder.create(it) }.toTypedArray()
          LookupManager.getInstance(project).showLookup(editor, items, "")
        }
      }
  }

  companion object {
    private fun getDocumentOffset(offset: Int, documentLength: Int): Int {
      return if (offset in 0..documentLength) offset else documentLength
    }

    val fixName: String by lazy { SpellCheckerBundle.message("change.to") }
  }
}