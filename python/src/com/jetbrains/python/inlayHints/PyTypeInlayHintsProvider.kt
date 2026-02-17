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
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.PyTypeParameter
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyCollectionType
import com.jetbrains.python.psi.types.PyInferredVarianceJudgment
import com.jetbrains.python.psi.types.PyTypeVarType
import com.jetbrains.python.psi.types.PyTypeVarType.Variance
import com.jetbrains.python.psi.types.TypeEvalContext

class PyTypeInlayHintsProvider : InlayHintsProvider {
  companion object {
    const val REVEAL_TYPE_OPTION_ID: String = "python.type.inlays.reveal_type"
    const val FUNCTION_RETURN_TYPE_OPTION_ID: String = "python.type.inlays.function.return"
    const val VARIANCE_OPTION_ID: String = "python.type.inlays.variance"
    const val PARAMETER_TYPE_ANNOTATION: String = "python.type.inlays.parameter.annotation"
  }

  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector = Collector()

  private class Collector : SharedBypassCollector {
    val returnTypeHintFormat = HintFormat.default.withFontSize(HintFontSize.ABitSmallerThanInEditor)
    val revealTypeHintFormat = returnTypeHintFormat.withHorizontalMargin(HintMarginPadding.MarginAndSmallerPadding)
    val varianceHintFormat = returnTypeHintFormat.withHorizontalMargin(HintMarginPadding.MarginAndSmallerPadding)

    override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
      val typeEvalContext = TypeEvalContext.codeAnalysis(element.project, element.containingFile)
      val resolveContext = PyResolveContext.defaultContext(typeEvalContext)

      sink.whenOptionEnabled(REVEAL_TYPE_OPTION_ID) {
        getInlaysForRevealType(element, sink, resolveContext)
      }

      sink.whenOptionEnabled(FUNCTION_RETURN_TYPE_OPTION_ID) {
        getInlaysForReturnType(element, sink, resolveContext)
      }

      sink.whenOptionEnabled(VARIANCE_OPTION_ID) {
        getInlaysForTypeVariableVariance(element, sink, resolveContext)
        getInlaysForTypeParameterVariance(element, sink, resolveContext)
      }

      sink.whenOptionEnabled(PARAMETER_TYPE_ANNOTATION) {
        getInlaysForParameterAnnotations(element, sink, resolveContext)
      }
    }

    private fun getInlaysForRevealType(element: PsiElement, sink: InlayTreeSink, resolveContext: PyResolveContext) {
      if (element !is PyCallExpression) return
      val callable = element.multiResolveCalleeFunction(resolveContext).singleOrNull()
      val typeEvalContext = resolveContext.typeEvalContext

      if (callable is PyFunction && callable.qualifiedName in listOf(PyTypingTypeProvider.REVEAL_TYPE,
                                                                     PyTypingTypeProvider.REVEAL_TYPE_EXT)) {
        val args = element.getArguments()

        if (args.size != 1) return
        val type = typeEvalContext.getType(args[0])

        val document = element.containingFile.fileDocument
        val lineNumber = document.getLineNumber(element.textRange.endOffset)
        sink.addPresentation(position = EndOfLinePosition(lineNumber), hintFormat = revealTypeHintFormat) {
          // use geTypeName here, because reveal_type should show the same as "Type Info" action
          text(PythonDocumentationProvider.getTypeName(type, typeEvalContext))
        }
      }
    }

    private fun getInlaysForReturnType(element: PsiElement, sink: InlayTreeSink, resolveContext: PyResolveContext) {
      val typeEvalContext = resolveContext.typeEvalContext
      val function = element.parent as? PyFunction ?: return
      if (element == function.nameIdentifier && function.annotationValue == null && function.typeCommentAnnotation == null) {
        val type = when (val type = typeEvalContext.getReturnType(function)) {
          is Any? if function.isAsync -> (type as? PyCollectionType)?.elementTypes[2]
          else -> type
        }

        val typeHint = PythonDocumentationProvider.getTypeHint(type, typeEvalContext)
        sink.addPresentation(position = InlineInlayPosition(function.parameterList.textRange.endOffset, true),
                             hintFormat = returnTypeHintFormat) {
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

    private fun getInlaysForTypeVariableVariance(element: PsiElement, sink: InlayTreeSink, resolveContext: PyResolveContext) {
      val refExpr = element as? PyReferenceExpression ?: return
      val tupleExpr = refExpr.parent as? PyTupleExpression
      val subscriptionExpr = refExpr.parent as? PySubscriptionExpression ?: tupleExpr?.parent as? PySubscriptionExpression ?: return
      if (subscriptionExpr.indexExpression != refExpr && subscriptionExpr.indexExpression != tupleExpr) return
      val qualifier = subscriptionExpr.qualifier as? PyReferenceExpression ?: return
      val qualifierType = resolveContext.typeEvalContext.getType(qualifier) as? PyClassLikeType ?: return
      if (PyTypingTypeProvider.GENERIC != qualifierType.classQName) return
      val typeVarType = PyTypingTypeProvider.getType(refExpr, resolveContext.typeEvalContext)?.get() as? PyTypeVarType ?: return
      if (typeVarType.variance == Variance.INVARIANT) return

      val inferredVariance = PyInferredVarianceJudgment.getInferredVariance(typeVarType, resolveContext.typeEvalContext)
      sink.addPresentation(inferredVariance, element)
    }

    private fun getInlaysForTypeParameterVariance(element: PsiElement, sink: InlayTreeSink, resolveContext: PyResolveContext) {
      if (element !is PyTypeParameter) return

      val inferredVariance = PyInferredVarianceJudgment.getInferredVariance(element, resolveContext.typeEvalContext)
      sink.addPresentation(inferredVariance, element)
    }

    fun InlayTreeSink.addPresentation(inferredVariance: Variance?, element: PsiElement) {
      val position = InlineInlayPosition(element.textRange.startOffset, false)
      if (inferredVariance == Variance.COVARIANT) {
        this.addPresentation(position = position, hintFormat = varianceHintFormat) { text("out") }
      }
      if (inferredVariance == Variance.CONTRAVARIANT) {
        this.addPresentation(position = position, hintFormat = varianceHintFormat) { text("in") }
      }
    }

    private fun getInlaysForParameterAnnotations(element: PsiElement, sink: InlayTreeSink, resolveContext: PyResolveContext) {
      val parameter = element as? PyNamedParameter ?: return

      if (parameter.annotationValue != null || parameter.typeCommentAnnotation != null) return

      val typeEvalContext = resolveContext.typeEvalContext

      val parameterType = typeEvalContext.getType(parameter) ?: return

      val typeHint = PythonDocumentationProvider.getTypeHint(parameterType, typeEvalContext)

      val offset = parameter.nameIdentifier?.textRange?.endOffset ?: parameter.textRange.endOffset

      sink.addPresentation(
        position = InlineInlayPosition(offset, true),
        hintFormat = HintFormat.default
      ) {
        text(": $typeHint")
      }
    }
  }
}