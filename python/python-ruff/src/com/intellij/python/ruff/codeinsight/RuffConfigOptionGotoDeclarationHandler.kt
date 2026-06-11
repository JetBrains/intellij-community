// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff.codeinsight

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.paths.WebReference
import com.intellij.psi.PsiElement
import com.intellij.python.ruff.RuffUtil.RUFF_WEBSITE
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKeySegment

/**
 * Handles documentation links for Ruff configuration options in TOML files.
 * For example:
 * ```toml
 * [tool.ruff.lint]
 * allowed-confusables = ["a"]
 * ```
 * Then it will link to `https://docs.astral.sh/ruff/settings/#lint_allowed-confusables`
 */
class RuffConfigOptionGotoDeclarationHandler : GotoDeclarationHandlerBase() {

  override fun getGotoDeclarationTarget(sourceElement: PsiElement?, editor: Editor): PsiElement? {
    sourceElement ?: return null
    if (sourceElement.containingFile !is TomlFile) return null
    return handleTomlFile(sourceElement)
  }

  private fun handleTomlFile(sourceElement: PsiElement): PsiElement? {
    val file = sourceElement.containingFile as? TomlFile ?: return null
    if (!file.isRuffConfigFile) return null

    val keySegment = sourceElement.parent as? TomlKeySegment ?: return null

    val configPath = keySegment.ruffConfigPath() ?: return null

    val formattedPath = if (configPath.isEmpty()) "top-level" else configPath.replace(".", "_")
    val url = "${RUFF_WEBSITE}/settings/#$formattedPath"

    return WebReference(keySegment, url).resolve()
  }
}
