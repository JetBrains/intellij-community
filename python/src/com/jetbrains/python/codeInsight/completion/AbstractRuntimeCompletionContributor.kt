// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.jetbrains.python.codeInsight.completion.runtime.patProvider.RemoteFilePathRetrievalService
import com.jetbrains.python.debugger.state.PyRuntime
import com.jetbrains.python.psi.PyStringElement

abstract class AbstractRuntimeCompletionContributor : CompletionContributor(), DumbAware {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val project = parameters.editor.project ?: return
    if (parameters.completionType == CompletionType.CLASS_NAME) return

    val context = ProcessingContext()
    if (!PlatformPatterns.psiElement().accepts(parameters.position, context)) return

    ProgressManager.checkCanceled()

    val service: PyRuntimeCompletionRetrievalService = getCompletionRetrievalService(project)
    if (!service.canComplete(parameters)) return

    fillCompletionVariantsFromRuntime(project, service, parameters, result)
  }

  abstract fun getRuntimeEnvService(project: Project): PyRuntime

  abstract fun getCompletionRetrievalService(project: Project): PyRuntimeCompletionRetrievalService

  private fun fillCompletionVariantsFromRuntime(
    project: Project,
    service: PyRuntimeCompletionRetrievalService,
    parameters: CompletionParameters,
    result: CompletionResultSet,
  ) {

    val runtimeResults: MutableMap<String, RuntimeLookupElement> =
      PyRuntimeCompletionUtils.createCompletionResultSet(service, getRuntimeEnvService(project), parameters)
        .associateByTo(hashMapOf(), { it.lookupString },
                       { RuntimeLookupElement(it, createCustomMatcher(parameters, result)) })

    if (runtimeResults.isEmpty()) {
      val remoteFileResults = project.service<RemoteFilePathRetrievalService>().retrieveRemoteFileLookupElements(parameters)
      runtimeResults.putAll(remoteFileResults)
    }

    // In general, [createCompletionResultSet] returns an empty list in two cases:
    // * If there is no runtime. In that case, it's better to return early to not waste CPU on runRemainingContributors and
    //   hash table access, even though these operations are fast.
    // * If there is nothing found. In that case, it's better to return early again, because there is nothing to add to the result.
    // * Other very improbable cases like the absence of the project assigned to the editor, which are handled in a defensive manner.
    if (runtimeResults.isEmpty()) return

    if (!result.isStopped) {
      result.runRemainingContributors(parameters) { item ->
        if (runtimeResults.remove(item.lookupElement.lookupString) != null) {
          val prioritizedCompletionResult = item.withLookupElement(PyRuntimeCompletionUtils.createPrioritizedLookupElement(item.lookupElement, true))
          result.withPrefixMatcher(item.prefixMatcher).passResult(prioritizedCompletionResult)
        }
        else {
          result.passResult(item)
        }
      }
    }

    runtimeResults.values.forEach {
      result.withPrefixMatcher(it.prefix).addElement(it.lookupElement)
    }
  }

  private fun createCustomMatcher(parameters: CompletionParameters, result: CompletionResultSet): PrefixMatcher {
    val currentElement = parameters.position
    if (currentElement is PyStringElement) {
      val newPrefix = TextRange.create(currentElement.contentRange.startOffset,
                                       parameters.offset - currentElement.textRange.startOffset).substring(currentElement.text)
      return PlainPrefixMatcher(newPrefix)
    }
    return PlainPrefixMatcher(result.prefixMatcher.prefix)
  }
}