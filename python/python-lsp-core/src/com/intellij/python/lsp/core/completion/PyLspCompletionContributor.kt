// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.customization.LspCompletionSupport
import com.intellij.platform.lsp.impl.features.completion.LspCompletionContributor
import com.intellij.python.lsp.core.PyLspToolIntegrationProvider

internal class PyLspCompletionContributor : CompletionContributor(), DumbAware {
  private val contributor = LspCompletionContributor()

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val psiFile = parameters.originalFile
    val project = psiFile.project
    if (project.isDefault) return

    val virtualFile = psiFile.originalFile.virtualFile?.let { (it as? VirtualFileWindow)?.delegate ?: it } ?: return
    val clientManager = LspClientManager.getInstance(project)
    val shouldRunCodeCompletion = LspIntegrationProvider.EP_NAME.extensionList.asSequence()
      .filterIsInstance<PyLspToolIntegrationProvider>()
      .flatMap { clientManager.getClients(it.javaClass) }
      .map { it.descriptor }
      .filter { it.isSupportedFile(virtualFile) }
      .any {
        val completionCustomizer = it.lspCustomization.completionCustomizer
        completionCustomizer is LspCompletionSupport && completionCustomizer.shouldRunCodeCompletion(parameters)
      }

    if (shouldRunCodeCompletion) {
      contributor.fillCompletionVariants(parameters, result)
      result.stopHere()
    }
  }
}
