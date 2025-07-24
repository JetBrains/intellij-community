// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inlayHints

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.impl.PresentationTreeBuilderImpl.Companion.MAX_SEGMENT_TEXT_LENGTH
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
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.REVEAL_TYPE
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.REVEAL_TYPE_EXT
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClassPattern
import com.jetbrains.python.psi.PyFunction
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

class PyTypeInlayHintsProvider : InlayHintsProvider {
  companion object {
    const val REVEAL_TYPE_OPTION_ID: String = "python.type.inlays.reveal_type"
    const val FUNCTION_RETURN_TYPE_OPTION_ID: String = "python.type.inlays.function.return"
    const val PSEUDO_KEYWORD_PATTERN_OPTION_ID: String = "python.type.inlays.pseudo.keyword.pattern"
  }

  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? = Collector()

  private class Collector : SharedBypassCollector {
    val hintFormat = HintFormat.default
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
      
      sink.whenOptionEnabled(PSEUDO_KEYWORD_PATTERN_OPTION_ID) {
        getPositionalPatternNames(element, sink, resolveContext)
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
    
    private fun getPositionalPatternNames(element: PsiElement, sink: InlayTreeSink, resolveContext: PyResolveContext) {
      val typeEvalContext = resolveContext.typeEvalContext
      val classPattern = element as? PyClassPattern ?: return
      val classType = typeEvalContext.getType(classPattern) as? PyClassType ?: return
      val matchArgs = PyClassPatternImpl.getMatchArgs(classType, typeEvalContext) ?: return
      classPattern.argumentList.patterns.mapIndexed { idx, pattern ->
        if (pattern is PyKeywordPattern) return // positional patterns can only be before keyword patterns
        val name = matchArgs.getOrNull(idx) ?: return@mapIndexed
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