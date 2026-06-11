// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff.codeinsight

import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter
import com.intellij.model.Pointer
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile
import com.intellij.python.ruff.RuffConfigOptionInfo
import com.intellij.python.ruff.RuffService
import com.intellij.python.ruff.RuffUtil
import org.jetbrains.annotations.Nls
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlHeaderOwner
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlTableHeader

/**
 * Provides documentation tooltips for Ruff configuration options in ruff config files.
 * When hovering over a configuration option key in a ruff config file, this provider
 * shows the option documentation fetched from the Ruff executable.
 */
class RuffConfigOptionDocumentationProvider : DocumentationTargetProvider {
  override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
    if (file !is TomlFile) {
      return emptyList()
    }

    if (!file.isRuffConfigFile) return emptyList()

    val element = file.findElementAt(offset) ?: return emptyList()

    val keySegment = element.parent as? TomlKeySegment ?: return emptyList()

    val configPath = keySegment.ruffConfigPath() ?: return emptyList()

    val configInfo = file.project.service<RuffService>().configOptions[configPath] ?: return emptyList()

    return listOf(RuffConfigOptionDocumentationTarget(configPath, configInfo, file.project))
  }

  /**
   * Gets the full path to a key in a TOML file.
   *
   * @param keyValue The key-value pair.
   * @return The full path to the key (e.g., "tool.ruff.lint.select").
   */
  private fun getFullPath(keyValue: TomlKeyValue): String {
    val parent = keyValue.parent

    if (parent is TomlTable) {
      val tableHeader = parent.header
      val tableKey = tableHeader.key

      if (tableKey != null) {
        return tableKey.text + "." + keyValue.key.text
      }
    }

    return keyValue.key.text
  }
}

/**
 * Documentation target for a Ruff configuration option.
 */
class RuffConfigOptionDocumentationTarget(
  @param:Nls private val configPath: String,
  private val configInfo: RuffConfigOptionInfo,
  private val project: Project,
) : DocumentationTarget {
  override fun createPointer(): Pointer<out DocumentationTarget> {
    return Pointer.hardPointer(this)
  }

  override fun computePresentation(): TargetPresentation {
    return TargetPresentation.builder(configPath)
      .icon(RuffUtil.getDefaultRuffIcon())
      .presentableText(configPath)
      .presentation()
  }

  override fun computeDocumentationHint(): @Nls String {
    return formatConfigOptionDocumentationHint(configInfo)
  }

  override fun computeDocumentation(): DocumentationResult {
    val html = formatConfigOptionDocumentation(configInfo, project)
    return DocumentationResult.documentation(html)
  }

  /**
   * Formats a hint for configuration option documentation.
   *
   * @param configInfo The configuration option information.
   * @return A formatted hint for the documentation.
   */
  @Nls
  private fun formatConfigOptionDocumentationHint(configInfo: RuffConfigOptionInfo): String {
    val firstLine = configInfo.doc.split("\n").firstOrNull() ?: ""
    return "<b>$configPath</b><br>$firstLine"
  }

  /**
   * Formats full documentation for a configuration option.
   *
   * @param configInfo The configuration option information.
   * @param project The project.
   * @return Formatted HTML documentation.
   */
  @Nls
  private fun formatConfigOptionDocumentation(configInfo: RuffConfigOptionInfo, project: Project): String {
    @Suppress("HardCodedStringLiteral")
    @Nls val markdown = buildString {
      append("# ${configPath.split(".").last()}\n\n")

      if (configInfo.deprecated != null) {
        append("**This option is deprecated**: ${configInfo.deprecated}\n\n")
      }

      append(configInfo.doc)
      append("\n\n")

      append("**Type:** `${configInfo.valueType}`\n\n")

      if (configInfo.default.isNotEmpty()) {
        append("**Default:** `${configInfo.default}`\n\n")
      }

      if (configInfo.scope != null) {
        append("**Scope:** ${configInfo.scope}\n\n")
      }

      if (configInfo.example.isNotEmpty()) {
        append("**Example:**\n\n```toml\n${configInfo.example}\n```\n\n")
      }
    }

    return DocMarkdownToHtmlConverter.convert(project, markdown)
  }
}

val TomlKey.fullName: String
  get() = parent.let {
    when (it) {
      is TomlKeyValue -> (it.parent as TomlTable).header.key!!.text + "." + text
      is TomlTableHeader -> text
      else -> error("invalid toml structure")
    }
  }

/**
 * gets the full name up until this KeySegment
 */
val TomlKeySegment.fullName: String?
  get() = parent.parent.let {
    when (it) {
      is TomlKeyValue -> when (val ancestor = it.parent) {
        is TomlHeaderOwner -> ancestor.header.key!!.text + "." + parent.text.take(textRangeInParent.endOffset)
        is TomlFile -> parent.text.take(textRangeInParent.endOffset)
        else -> null
      }
      is TomlTableHeader -> parent.text.take(textRangeInParent.endOffset)
      else -> null
    }
  }
