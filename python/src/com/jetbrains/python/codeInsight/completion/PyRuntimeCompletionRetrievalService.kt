// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.LanguageNamesValidation
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.util.ProcessingContext
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.state.PyRuntime
import com.jetbrains.python.debugger.values.DataFrameDebugValue
import com.jetbrains.python.debugger.values.completePandasDataFrameColumns
import com.jetbrains.python.psi.PyStringElement
import java.util.concurrent.Callable

enum class PyRuntimeCompletionType {
  DYNAMIC_CLASS, DATA_FRAME_COLUMNS, DICT_KEYS
}


/**
 * @param completionType - type of completion items to choose post-processing function in the future
 */
data class CompletionResultData(val setOfCompletionItems: Set<String>, val completionType: PyRuntimeCompletionType)

private fun processDataFrameColumns(columns: Set<String>,
                                    needValidatorCheck: Boolean,
                                    elementOnPosition: PsiElement,
                                    project: Project,
                                    stringPresentation: String,
                                    ignoreML: Boolean = true): List<LookupElement> {
  val validator = LanguageNamesValidation.INSTANCE.forLanguage(PythonLanguage.getInstance())
  return columns.mapNotNull { column ->
    when {
      !needValidatorCheck && elementOnPosition !is PyStringElement -> {
        "'${StringUtil.escapeStringCharacters(column.length, column, "'", false, StringBuilder())}'"
      }
      !needValidatorCheck -> StringUtil.escapeStringCharacters(column.length, column, "'\"", false, StringBuilder())
      validator.isIdentifier(column, project) -> column
      else -> null
    }?.let {
      val lookupElement = LookupElementBuilder.create(it).withTypeText(stringPresentation).withIcon(
        IconManager.getInstance().getPlatformIcon(PlatformIcons.Parameter))
      createPrioritizedLookupElement(lookupElement, ignoreML)
    }
  }
}

private fun postProcessingChildren(completionResultData: CompletionResultData,
                                   candidate: PyObjectCandidate,
                                   parameters: CompletionParameters): List<LookupElement> {
  return when (completionResultData.completionType) {
    PyRuntimeCompletionType.DATA_FRAME_COLUMNS -> {
      val project = parameters.editor.project ?: return emptyList()
      val needValidatorCheck = (candidate.pyQualifiedExpressionList.lastOrNull()?.delimiter
                                ?: candidate.psiName.delimiter) != PyTokenTypes.LBRACKET
      processDataFrameColumns(completionResultData.setOfCompletionItems,
                              needValidatorCheck,
                              parameters.position,
                              project,
                              PyBundle.message("pandas.completion.type.text", candidate.psiName.pyQualifiedName))
    }
    PyRuntimeCompletionType.DICT_KEYS -> {
      val setOfCompletionItems = completionResultData.setOfCompletionItems.filter { it != "__len__" }.map {
        it.removeSurrounding("\'")
      }.toSet()
      val project = parameters.editor.project ?: return emptyList()
      processDataFrameColumns(setOfCompletionItems,
                              false,
                              parameters.position,
                              project,
                              PyBundle.message("dict.completion.type.text"))
    }
    PyRuntimeCompletionType.DYNAMIC_CLASS -> proceedPyValueChildrenNames(completionResultData.setOfCompletionItems,
                                                                         candidate.psiName.pyQualifiedName)
  }
}

private fun proceedPyValueChildrenNames(childrenNodes: Set<String>,
                                        stringPresentation: String,
                                        ignoreML: Boolean = true): List<LookupElement> {
  return childrenNodes.map {
    val lookupElement = LookupElementBuilder.create(it).withTypeText(stringPresentation).withIcon(
      IconManager.getInstance().getPlatformIcon(PlatformIcons.Parameter))
    createPrioritizedLookupElement(lookupElement, ignoreML)
  }
}

interface PyRuntimeCompletionRetrievalService {
  /**
   * This function checks additional conditions before calling completion
   * @return true - if all checks pass / false - if not
   */
  fun canComplete(parameters: CompletionParameters): Boolean

  fun extractItemsForCompletion(result: Pair<XValueNodeImpl, List<PyQualifiedExpressionItem>>?,
                                candidate: PyObjectCandidate, completionType: CompletionType): CompletionResultData? {
    val (node, listOfCalls) = result ?: return null
    val debugValue = node.valueContainer
    if (debugValue is DataFrameDebugValue) {
      val dfColumns = completePandasDataFrameColumns(debugValue.treeColumns, listOfCalls.map { it.pyQualifiedName }) ?: return null
      return CompletionResultData(dfColumns, PyRuntimeCompletionType.DATA_FRAME_COLUMNS)
    }
    if (completionType == CompletionType.BASIC) return null
    computeChildrenIfNeeded(node)
    if ((debugValue as PyDebugValue).qualifiedType == "builtins.dict") {
      return CompletionResultData(node.loadedChildren.mapNotNull { (it as? XValueNodeImpl)?.name }.toSet(),
                                  PyRuntimeCompletionType.DICT_KEYS)
    }
    return CompletionResultData(node.loadedChildren.mapNotNull { (it as? XValueNodeImpl)?.name }.toSet(),
                                PyRuntimeCompletionType.DYNAMIC_CLASS)
  }
}

abstract class AbstractRuntimeCompletionContributor : CompletionContributor(), DumbAware {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val project = parameters.editor.project ?: return
    if (parameters.completionType == CompletionType.CLASS_NAME) return

    val context = ProcessingContext()
    if (!PlatformPatterns.psiElement().accepts(parameters.position, context)) return

    ProgressManager.checkCanceled()

    val service: PyRuntimeCompletionRetrievalService = getCompletionRetrievalService(project)
    if (!service.canComplete(parameters)) return

    fillCompletionVariantsFromRuntime(project, service, parameters, createCustomMatcher(parameters, result))
  }

  private fun fillCompletionVariantsFromRuntime(
    project: Project,
    service: PyRuntimeCompletionRetrievalService,
    parameters: CompletionParameters,
    result: CompletionResultSet,
  ) {
    val runtimeResults: MutableMap<String, LookupElement> =
      createCompletionResultSet(service, getRuntimeEnvService(project), parameters)
        .associateByTo(hashMapOf()) { lookupElement -> lookupElement.lookupString }

    // In general, [createCompletionResultSet] returns an empty list in two cases:
    // * If there is no runtime. In that case, it's better to return early to not waste CPU on runRemainingContributors and
    //   hash table access, even though these operations are fast.
    // * If there is nothing found. In that case, it's better to return early again, because there is nothing to add to the result.
    // * Other very improbable cases like absence of the project assigned to the editor, which are handled in a defensive manner.
    if (runtimeResults.isNotEmpty()) {
      if (!result.isStopped) {
        result.runRemainingContributors(parameters) { item ->
          if (runtimeResults.remove(item.lookupElement.lookupString) != null) {
            val prioritizedCompletionResult = item.withLookupElement(createPrioritizedLookupElement(item.lookupElement, true))
            result.passResult(prioritizedCompletionResult)
          }
          else {
            result.passResult(item)
          }
        }
      }

      result.addAllElements(runtimeResults.values)
    }
  }

  abstract fun getRuntimeEnvService(project: Project): PyRuntime

  abstract fun getCompletionRetrievalService(project: Project): PyRuntimeCompletionRetrievalService
}


fun createCompletionResultSet(retrievalService: PyRuntimeCompletionRetrievalService,
                              runtimeService: PyRuntime,
                              parameters: CompletionParameters): List<LookupElement> {
  if (!retrievalService.canComplete(parameters)) return emptyList()
  val project = parameters.editor.project ?: return emptyList()
  val treeNodeList = runtimeService.getGlobalPythonVariables(parameters.originalFile.virtualFile, project, parameters.editor)
                     ?: return emptyList()
  val pyObjectCandidates = getCompleteAttribute(parameters)

  return ApplicationUtil.runWithCheckCanceled(Callable {
    return@Callable pyObjectCandidates.flatMap { candidate ->
      val parentNode = getParentNodeByName(treeNodeList, candidate.psiName.pyQualifiedName, parameters.completionType)
      val valueContainer = parentNode?.valueContainer
      if (valueContainer is PyDebugValue) {
        /**
         * Don't need to send requests to jupyter server about Python's module,
         * because LegacyCompletionContributor provide completion items for Python's modules
         * @see com.intellij.codeInsight.completion.LegacyCompletionContributor
         */
        if (valueContainer.type == "module") return@flatMap emptyList()
        if (checkDelimiterByType(valueContainer.qualifiedType, candidate.psiName.delimiter)) return@flatMap emptyList()
      }
      getSetOfChildrenByListOfCall(parentNode, candidate.pyQualifiedExpressionList, parameters.completionType)
        .let { retrievalService.extractItemsForCompletion(it, candidate, parameters.completionType) }
        ?.let { postProcessingChildren(it, candidate, parameters) }
      ?: emptyList()
    }
  }, ProgressManager.getInstance().progressIndicator)
}