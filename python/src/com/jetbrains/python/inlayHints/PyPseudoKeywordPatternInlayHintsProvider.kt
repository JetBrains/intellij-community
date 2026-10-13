// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inlayHints

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hints.declarative.HintFontSize
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.HintMarginPadding
import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.InlayActionHandler
import com.intellij.codeInsight.hints.declarative.InlayActionPayload
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.PsiNavigateUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.text.nullize
import com.jetbrains.python.psi.PyClassPattern
import com.jetbrains.python.psi.PyKeywordPattern
import com.jetbrains.python.psi.impl.PyClassPatternImpl
import com.jetbrains.python.psi.impl.references.PyKeywordPatternReference
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Provides inlays for positional patterns in class patterns, showing pseudo keyword argument names.
 */
class PyPseudoKeywordPatternInlayHintsProvider : InlayHintsProvider {

  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector = Collector()

  private class Collector : SharedBypassCollector {
    private val hintFormat = HintFormat.default
      .withFontSize(HintFontSize.ABitSmallerThanInEditor)
      .withHorizontalMargin(HintMarginPadding.MarginAndSmallerPadding)

    override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
      val typeEvalContext = TypeEvalContext.codeAnalysis(element.project, element.containingFile)
      val classPattern = element as? PyClassPattern ?: return
      val classType = typeEvalContext.getType(classPattern) as? PyClassType ?: return
      val matchArgs = PyClassPatternImpl.getMatchArgs(classType, typeEvalContext) ?: return
      classPattern.argumentList.patterns.mapIndexed { idx, pattern ->
        if (pattern is PyKeywordPattern) return // positional patterns can only be before keyword patterns
        val name = matchArgs.getOrNull(idx)?.nullize() ?: return@mapIndexed
        sink.addPresentation(position = InlineInlayPosition(pattern.textRange.startOffset, false), hintFormat = hintFormat) {
          val navigationData = InlayActionData(
            PyPseudoKeywordPatternInlayActionHandler.Payload(SmartPointerManager.createPointer(classPattern), name),
            PyPseudoKeywordPatternInlayActionHandler.HANDLER_NAME
          )
          text(name, actionData = navigationData)
          text("=")
        }
      }
    }
  }
}

class PyPseudoKeywordPatternInlayActionHandler(private val cs: CoroutineScope) : InlayActionHandler {
  companion object {
    const val HANDLER_NAME: String = "py.pseudo.keyword.pattern"
  }

  data class Payload(val classPattern: SmartPsiElementPointer<PyClassPattern>, val keyword: String) : InlayActionPayload

  @RequiresEdt
  override fun handleClick(e: EditorMouseEvent, payload: InlayActionPayload) {
    payload as Payload

    cs.launch {
      val resolveResults = readAction {
        val classPattern = payload.classPattern.element ?: return@readAction null
        val keyword = payload.keyword
        val typeEvalContext = TypeEvalContext.userInitiated(classPattern.project, classPattern.containingFile)
        PyKeywordPatternReference.resolveKeyword(classPattern, keyword, PyResolveContext.defaultContext(typeEvalContext))
      }
      val element = resolveResults?.firstOrNull()?.element ?: run {
        withContext(Dispatchers.EDT) {
          HintManager.getInstance().showInformationHint(e.editor, CodeInsightBundle.message("declaration.navigation.nowhere.to.go"))
        }
        return@launch
      }

      withContext(Dispatchers.EDT) {
        PsiNavigateUtil.navigate(element)
      }
    }
  }
}