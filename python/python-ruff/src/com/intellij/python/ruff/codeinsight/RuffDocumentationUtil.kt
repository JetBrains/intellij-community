// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff.codeinsight

import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter
import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.ruff.RuffRuleInfo
import com.intellij.python.ruff.RuffUtil
import org.jetbrains.annotations.Nls
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable

/**
 * Utility class for Ruff documentation-related functionality.
 * Contains common code shared between Ruff-related classes for handling TOML configuration and documentation.
 */
object RuffDocumentationUtil {
  val CODE_PATTERN = Regex("[A-Z]+[0-9]{3}")

  val RUFF_LINTER_PATTERN = Regex("[A-Z]+")

  val SUPPRESSION_PATTERN = Regex("# (?:ruff: )?noqa: ?([A-Z]+[0-9]{3}(?:, ?([A-Z]+[0-9]{3}))*)?")

  val ERROR_CODE_ARRAY_KEYS = setOf(
    "select", "ignore", "fixable", "unfixable",
    "extend-fixable", "extend-safe-fixes", "extend-select",
    "extend-unsafe-fixes", "extend-ignore"
  ).map { "lint.$it" }

  fun isRuffErrorCode(code: String): Boolean {
    return CODE_PATTERN.matches(code)
  }

  fun TomlLiteral.isRuffCodeElement(): Boolean {
    val array = this.parent as? TomlArray ?: return false

    val keyValue = array.parent as? TomlKeyValue ?: return false

    val configPath = (keyValue.key.children.last() as TomlKeySegment).ruffConfigPath().orEmpty()

    return configPath in ERROR_CODE_ARRAY_KEYS || Regex("""lint\.(extend-)?per-file-ignores\..*""") matches configPath
  }

  @Nls
  fun formatRuleDocumentationHint(ruleInfo: RuffRuleInfo): @NlsSafe String {
    return "<b>${ruleInfo.name}</b>: ${ruleInfo.code}<br>${ruleInfo.summary}"
  }

  @Nls
  fun formatRuleDocumentation(ruleInfo: RuffRuleInfo, project: Project): String {
    @Suppress("HardCodedStringLiteral")
    return DocMarkdownToHtmlConverter.convert(project, """
      # ${ruleInfo.name} (${ruleInfo.code})
      Derived from the **${ruleInfo.linter}** linter.
      
      """.trimIndent() + ruleInfo.explanation)
  }
}

class RuffRuleDocumentationTarget(private val ruleInfo: RuffRuleInfo, private val project: Project) : DocumentationTarget {
  override fun createPointer(): Pointer<out DocumentationTarget> {
    return Pointer.hardPointer(this)
  }

  override fun computePresentation(): TargetPresentation {
    return TargetPresentation.builder(ruleInfo.code)
      .icon(RuffUtil.getDefaultRuffIcon())
      .presentableText("${ruleInfo.code}: ${ruleInfo.name}")
      .presentation()
  }

  override fun computeDocumentationHint(): @Nls String {
    return RuffDocumentationUtil.formatRuleDocumentationHint(ruleInfo)
  }

  override fun computeDocumentation(): DocumentationResult {
    val html = RuffDocumentationUtil.formatRuleDocumentation(ruleInfo, project)
    return DocumentationResult.documentation(html)
  }
}

/**
 * warning: is not robust against keys that contain dots (but no ruff keys do 🙂)
 */
val TomlKeyValue.fullName: String
  get() = (parent as TomlTable).header.key?.text + "." + key.text

val TomlFile.isRuffConfigFile: Boolean
  get() = name == PY_PROJECT_TOML || name == "ruff.toml" || name == ".ruff.toml"

fun TomlKeySegment.ruffConfigPath(): @NlsSafe String? {
  val fullName = fullName ?: return null
  return when {
    this.containingFile.name != PY_PROJECT_TOML -> fullName
    fullName.startsWith("tool.ruff.") -> fullName.removePrefix("tool.ruff.")
    else -> null
  }
}
