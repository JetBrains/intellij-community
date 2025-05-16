// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inlayHints

import com.intellij.codeInsight.hints.declarative.EndOfLinePosition
import com.intellij.codeInsight.hints.declarative.HintFontSize
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.HintMarginPadding
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.codeInsight.hints.declarative.impl.PresentationTreeBuilderImpl.Companion.MAX_SEGMENT_TEXT_LENGTH
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.REVEAL_TYPE
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.REVEAL_TYPE_EXT
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.TypeEvalContext

class PyTypeInlayHintsProvider : InlayHintsProvider {
  companion object {
    const val REVEAL_TYPE_OPTION_ID: String = "python.type.inlays.reveal_type"
    const val FUNCTION_RETURN_TYPE_OPTION_ID: String = "python.type.inlays.function.return"
  }

  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? = Collector()

  private class Collector : SharedBypassCollector {
    val hintFormat = HintFormat.Companion.default
      .withFontSize(HintFontSize.ABitSmallerThanInEditor)
      .withHorizontalMargin(HintMarginPadding.MarginAndSmallerPadding)

    override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
      val typeEvalContext = TypeEvalContext.codeAnalysis(element.project, element.containingFile)
      val resolveContext = PyResolveContext.defaultContext(typeEvalContext)

      sink.whenOptionEnabled(REVEAL_TYPE_OPTION_ID) {
        getInlaysForRevealType(element, sink, resolveContext)
      }

      sink.whenOptionEnabled(FUNCTION_RETURN_TYPE_OPTION_ID) {
        getInlaysForReturnType(element, sink, resolveContext)
      }
    }

    private fun getInlaysForRevealType(element: PsiElement, sink: InlayTreeSink, resolveContext: PyResolveContext) {
      if (element !is PyCallExpression) return
      val callable = element.multiResolveCalleeFunction(resolveContext).singleOrNull()
      val typeEvalContext = resolveContext.typeEvalContext

      if (callable is PyFunction && callable.qualifiedName in listOf(REVEAL_TYPE, REVEAL_TYPE_EXT)) {
        val args = element.getArguments()

        if (args.size != 1) return
        val type = typeEvalContext.getType(args[0])

        val document = element.containingFile.fileDocument
        val lineNumber = document.getLineNumber(element.textRange.endOffset)
        sink.addPresentation(position = EndOfLinePosition(lineNumber), hintFormat = hintFormat) {
          // use geTypeName here, because reveal_type should show the same as "Type Info" action
          text(PythonDocumentationProvider.getTypeName(type, typeEvalContext))
        }
      }
    }

    private fun getInlaysForReturnType(element: PsiElement, sink: InlayTreeSink, resolveContext: PyResolveContext) {
      val typeEvalContext = resolveContext.typeEvalContext
      val function = element.parent as? PyFunction ?: return
      if (element == function.nameIdentifier && function.annotationValue == null && function.typeCommentAnnotation == null) {
        val type = typeEvalContext.getReturnType(function)
        if (PyTypeChecker.isUnknown(type, typeEvalContext)) return
        val typeHint = PythonDocumentationProvider.getTypeHint(type, typeEvalContext)
        sink.addPresentation(position = InlineInlayPosition(function.parameterList.textRange.endOffset, true), hintFormat = hintFormat) {
          text("-> ")
          if (typeHint.length >= MAX_SEGMENT_TEXT_LENGTH) {
            // Platform doesn't allow one text node to be more than 30 characters, but that might not be enough for some types,
            // for example, 'Generator[str | int, None, int]' is already 31 chars long
            text(typeHint.substring(0, MAX_SEGMENT_TEXT_LENGTH))
            text(typeHint.substring(MAX_SEGMENT_TEXT_LENGTH))
          }
          else {
            text(typeHint)
          }
        }
      }
    }
  }
}