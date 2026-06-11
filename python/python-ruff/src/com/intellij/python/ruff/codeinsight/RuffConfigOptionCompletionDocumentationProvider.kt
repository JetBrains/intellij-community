// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff.codeinsight

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.components.service
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.LookupElementDocumentationTargetProvider
import com.intellij.psi.PsiFile
import com.intellij.python.ruff.RuffService
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable

/**
 * Provides documentation for Ruff configuration option completion items.
 * This provider serves the same documentation as [RuffConfigOptionDocumentationProvider]
 * but for lookup elements in code completion.
 */
private class RuffConfigOptionCompletionDocumentationProvider : LookupElementDocumentationTargetProvider {
  override fun documentationTarget(psiFile: PsiFile, element: LookupElement, offset: Int): DocumentationTarget? {
    val anchorElement = (if (offset - 1 >= 0) psiFile.findElementAt(offset - 1) else psiFile.findElementAt(offset))
    val anchorParent = anchorElement?.parent
    val configPath = when (anchorParent) {
      is TomlKeySegment ->
        anchorParent.fullName?.dropLastWhile { it != '.' } ?: return null
      is TomlKeyValue ->
        anchorParent.fullName + "."
     is PsiFile if anchorElement.prevSibling is TomlTable ->
        (anchorElement.prevSibling as TomlTable).header.key?.fullName?.plus(".") ?: return null
      else ->
        return null
    } + element.lookupString

    val configInfo = psiFile.project.service<RuffService>().configOptions[configPath.removePrefix("tool.ruff.")] ?: return null
    
    return RuffConfigOptionDocumentationTarget(configPath, configInfo, psiFile.project)
  }
}
