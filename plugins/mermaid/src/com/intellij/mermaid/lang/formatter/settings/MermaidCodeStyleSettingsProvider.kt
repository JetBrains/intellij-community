// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.formatter.settings

import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.lang.Language
import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.psi.codeStyle.CodeStyleConfigurable
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizableOptions
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider

internal class MermaidCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
  override fun getLanguage(): Language = MermaidLanguage

  override fun createConfigurable(baseSettings: CodeStyleSettings, modelSettings: CodeStyleSettings): CodeStyleConfigurable {
    return MermaidCodeStyleConfigurable(baseSettings, modelSettings)
  }

  override fun getConfigurableDisplayName() = MermaidBundle.message("mermaid.settings.name")

  override fun customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) {
    when (settingsType) {
      SettingsType.BLANK_LINES_SETTINGS -> {
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::MIN_LINES_AROUND_STRUCTURED_STATEMENTS.name,
          MermaidBundle.message("mermaid.style.settings.blank.lines.around.structured.statements"),
          CodeStyleSettingsCustomizableOptions.getInstance().BLANK_LINES
        )
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::MIN_LINES_BETWEEN_STRUCTURED_STATEMENTS.name,
          MermaidBundle.message("mermaid.style.settings.blank.lines.between.structured.statements"),
          CodeStyleSettingsCustomizableOptions.getInstance().BLANK_LINES
        )
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::MIN_LINES_BETWEEN_OTHER_STATEMENTS.name,
          MermaidBundle.message("mermaid.style.settings.blank.lines.between.other.statements"),
          CodeStyleSettingsCustomizableOptions.getInstance().BLANK_LINES
        )
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::MIN_LINES_WITHIN_STRUCTURES.name,
          MermaidBundle.message("mermaid.style.settings.blank.lines.within.structures"),
          CodeStyleSettingsCustomizableOptions.getInstance().BLANK_LINES
        )

        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::KEEP_LINES_AROUND_STRUCTURED_STATEMENTS.name,
          MermaidBundle.message("mermaid.style.settings.blank.lines.around.structured.statements"),
          CodeStyleSettingsCustomizableOptions.getInstance().BLANK_LINES_KEEP
        )
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::KEEP_LINES_BETWEEN_STRUCTURED_STATEMENTS.name,
          MermaidBundle.message("mermaid.style.settings.blank.lines.between.structured.statements"),
          CodeStyleSettingsCustomizableOptions.getInstance().BLANK_LINES_KEEP
        )
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::KEEP_LINES_BETWEEN_OTHER_STATEMENTS.name,
          MermaidBundle.message("mermaid.style.settings.blank.lines.between.other.statements"),
          CodeStyleSettingsCustomizableOptions.getInstance().BLANK_LINES_KEEP
        )
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::KEEP_LINES_WITHIN_STRUCTURES.name,
          MermaidBundle.message("mermaid.style.settings.blank.lines.within.structures"),
          CodeStyleSettingsCustomizableOptions.getInstance().BLANK_LINES_KEEP
        )
      }

      SettingsType.SPACING_SETTINGS -> {
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::FORCE_ONE_SPACE_BETWEEN_WORDS.name,
          MermaidBundle.message("mermaid.style.settings.spacing.between.words"),
          MermaidBundle.message("mermaid.style.settings.spacing.force.one.space")
        )

        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::AROUND_EQUALITY.name,
          MermaidBundle.message("mermaid.style.settings.spacing.around.equality"),
          MermaidBundle.message("mermaid.style.settings.spacing.punctuation")
        )
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::BEFORE_COMMA.name,
          MermaidBundle.message("mermaid.style.settings.spacing.before.comma"),
          MermaidBundle.message("mermaid.style.settings.spacing.punctuation")
        )
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::AFTER_COMMA.name,
          MermaidBundle.message("mermaid.style.settings.spacing.after.comma"),
          MermaidBundle.message("mermaid.style.settings.spacing.punctuation")
        )
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::BEFORE_COLON.name,
          MermaidBundle.message("mermaid.style.settings.spacing.before.colon"),
          MermaidBundle.message("mermaid.style.settings.spacing.punctuation")
        )
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::AFTER_COLON.name,
          MermaidBundle.message("mermaid.style.settings.spacing.after.colon"),
          MermaidBundle.message("mermaid.style.settings.spacing.punctuation")
        )
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::BEFORE_SEMICOLON.name,
          MermaidBundle.message("mermaid.style.settings.spacing.before.semicolon"),
          MermaidBundle.message("mermaid.style.settings.spacing.punctuation")
        )

        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::BEFORE_OPEN_CURLY.name,
          MermaidBundle.message("mermaid.style.settings.spacing.before.open.curly"),
          MermaidBundle.message("mermaid.style.settings.spacing.parentheses")
        )
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::BEFORE_OPEN_ROUND.name,
          MermaidBundle.message("mermaid.style.settings.spacing.before.open.round"),
          MermaidBundle.message("mermaid.style.settings.spacing.parentheses")
        )
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::WITHIN_CURLY.name,
          MermaidBundle.message("mermaid.style.settings.spacing.within.curly"),
          MermaidBundle.message("mermaid.style.settings.spacing.parentheses")
        )
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::WITHIN_ROUND.name,
          MermaidBundle.message("mermaid.style.settings.spacing.within.round"),
          MermaidBundle.message("mermaid.style.settings.spacing.parentheses")
        )
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::WITHIN_SQUARE.name,
          MermaidBundle.message("mermaid.style.settings.spacing.within.square"),
          MermaidBundle.message("mermaid.style.settings.spacing.parentheses")
        )

        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::WITHIN_NODE_SHAPES.name,
          MermaidBundle.message("mermaid.style.settings.spacing.within.node.shapes"),
          MermaidBundle.message("mermaid.style.settings.spacing.node.shapes")
        )
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::BETWEEN_NODE_ID_AND_NODE_SHAPE.name,
          MermaidBundle.message("mermaid.style.settings.spacing.between.node.id.and.node.shape"),
          MermaidBundle.message("mermaid.style.settings.spacing.node.shapes")
        )

        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::WITHIN_ANNOTATION_BRACES.name,
          MermaidBundle.message("mermaid.style.settings.spacing.within.annotation.braces"),
          MermaidBundle.message("mermaid.style.settings.spacing.annotation")
        )
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::BETWEEN_STATE_AND_ANNOTATION.name,
          MermaidBundle.message("mermaid.style.settings.spacing.between.state.and.annotation"),
          MermaidBundle.message("mermaid.style.settings.spacing.annotation")
        )

        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::AROUND_ARROW.name,
          MermaidBundle.message("mermaid.style.settings.spacing.around.arrow"),
          MermaidBundle.message("mermaid.style.settings.spacing.arrows")
        )
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::BEETWEEN_LINE_TYPE_AND_RELATION_TYPE.name,
          MermaidBundle.message("mermaid.style.settings.spacing.between.line.and.relation"),
          MermaidBundle.message("mermaid.style.settings.spacing.arrows")
        )

        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::AROUND_INLINE_ARROW_TEXT.name,
          MermaidBundle.message("mermaid.style.settings.spacing.around.inline.around.text"),
          MermaidBundle.message("mermaid.style.settings.spacing.flowchart.link.text")
        )
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::WITHIN_ARROW_TEXT_SEP.name,
          MermaidBundle.message("mermaid.style.settings.spacing.within.sep.after.arrow"),
          MermaidBundle.message("mermaid.style.settings.spacing.flowchart.link.text")
        )
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::BEFORE_ARROW_TEXT_WITHIN_SEP.name,
          MermaidBundle.message("mermaid.style.settings.spacing.before.arrow.text.sep"),
          MermaidBundle.message("mermaid.style.settings.spacing.flowchart.link.text")
        )
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::AFTER_ARROW_TEXT_WITHIN_SEP.name,
          MermaidBundle.message("mermaid.style.settings.spacing.after.arrow.text.sep"),
          MermaidBundle.message("mermaid.style.settings.spacing.flowchart.link.text")
        )

        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::AROUND_STYLE_SEPARATOR.name,
          MermaidBundle.message("mermaid.style.settings.spacing.around.style.separator"),
          MermaidBundle.message("mermaid.style.settings.spacing.other")
        )
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::BEFORE_GENERIC.name,
          MermaidBundle.message("mermaid.style.settings.spacing.before.generic"),
          MermaidBundle.message("mermaid.style.settings.spacing.other")
        )
      }

      else -> {}
    }
  }

  override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings {
    return MermaidCustomCodeStyleSettings(settings)
  }

  override fun getIndentOptionsEditor(): IndentOptionsEditor = SmartIndentOptionsEditor()

  override fun getCodeSample(settingsType: SettingsType): String {
    val sampleName = when (settingsType) {
      SettingsType.INDENT_SETTINGS -> "indent_settings.mermaid"
      SettingsType.BLANK_LINES_SETTINGS -> "blank_lines_settings.mermaid"
      SettingsType.SPACING_SETTINGS -> "spacing_settings.mermaid"
      else -> "default.mermaid"
    }
    val codeSample = this::class.java.getResourceAsStream(sampleName)?.bufferedReader()?.use { it.readText() }
    return codeSample ?: "Failed to get predefined code sample"
  }
}
