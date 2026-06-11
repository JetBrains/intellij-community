// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff.codeinsight

import com.intellij.codeInsight.highlighting.TooltipLinkHandler
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.python.ruff.RuffBundle
import com.intellij.python.ruff.RuffRuleInfo
import com.intellij.python.ruff.RuffService
import org.jetbrains.annotations.NotNull

/**
 * Handles tooltip links to Ruff rule documentation.
 * Links are in the format: https://docs.astral.sh/ruff/rules/rule-name
 * The documentation is retrieved from RuffCodeService.
 */
class RuffTooltipLinkHandler : TooltipLinkHandler() {

  override fun getDescription(@NotNull refSuffix: String, @NotNull editor: Editor): String? {
    val project = editor.project ?: return null
    if (project.isDisposed) return null

    val ruleName = extractRuleName(refSuffix) ?: return null

    val ruleInfo = findRuleByName(project, ruleName) ?: return null

    return RuffDocumentationUtil.formatRuleDocumentation(ruleInfo, project)
  }

  override fun getDescriptionTitle(@NotNull refSuffix: String, @NotNull editor: Editor): String {
    return RuffBundle.message("inspection.message.ruff.rule.documentation")
  }

  /**
   * Extracts the rule name from the URL suffix.
   * @param refSuffix The suffix of the URL after the prefix (e.g., "rules/useless-expression")
   * @return The rule name (e.g., "useless-expression") or null if the format is invalid
   */
  private fun extractRuleName(refSuffix: String): String? {
    // The refSuffix should be in the format "rules/rule-name"
    val parts = refSuffix.split("/")
    if (parts.size != 2 || parts[0] != "rules") {
      return null
    }
    return parts[1]
  }

  private fun findRuleByName(project: Project, ruleName: String): RuffRuleInfo? {
    val ruffService = project.service<RuffService>()
    
    return ruffService.ruleInformation.values.find { ruleInfo ->
      ruleInfo.name.lowercase() == ruleName
    }
  }
}
