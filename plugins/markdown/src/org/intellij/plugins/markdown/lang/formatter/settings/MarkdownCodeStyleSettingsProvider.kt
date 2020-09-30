// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter.settings

import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.*
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.MarkdownLanguage

internal class MarkdownCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
  override fun getLanguage(): Language = MarkdownLanguage.INSTANCE

  override fun createConfigurable(baseSettings: CodeStyleSettings, modelSettings: CodeStyleSettings): CodeStyleConfigurable {
    return MarkdownCodeStyleConfigurable(baseSettings, modelSettings)
  }

  override fun getConfigurableDisplayName() = MarkdownBundle.message("markdown.settings.name")

  override fun customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) {
    when (settingsType) {
      SettingsType.WRAPPING_AND_BRACES_SETTINGS -> {
        consumer.showStandardOptions("RIGHT_MARGIN", "WRAP_ON_TYPING")
      }
      SettingsType.BLANK_LINES_SETTINGS -> {
        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::MAX_LINES_AROUND_HEADER.name,
          MarkdownBundle.message("markdown.style.settings.blank.lines.around.header"),
          CodeStyleSettingsCustomizableOptions.getInstance().BLANK_LINES
        )

        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::MAX_LINES_AROUND_BLOCK_ELEMENTS.name,
          MarkdownBundle.message("markdown.style.settings.blank.lines.around.block.elements"),
          CodeStyleSettingsCustomizableOptions.getInstance().BLANK_LINES
        )

        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::MAX_LINES_BETWEEN_PARAGRAPHS.name,
          MarkdownBundle.message("markdown.style.settings.blank.lines.between.paragraphs"),
          CodeStyleSettingsCustomizableOptions.getInstance().BLANK_LINES
        )

        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::MIN_LINES_AROUND_HEADER.name,
          MarkdownBundle.message("markdown.style.settings.blank.lines.around.header"),
          CodeStyleSettingsCustomizableOptions.getInstance().BLANK_LINES_KEEP
        )

        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::MIN_LINES_AROUND_BLOCK_ELEMENTS.name,
          MarkdownBundle.message("markdown.style.settings.blank.lines.around.block.elements"),
          CodeStyleSettingsCustomizableOptions.getInstance().BLANK_LINES_KEEP
        )

        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::MIN_LINES_BETWEEN_PARAGRAPHS.name,
          MarkdownBundle.message("markdown.style.settings.blank.lines.between.paragraphs"),
          CodeStyleSettingsCustomizableOptions.getInstance().BLANK_LINES_KEEP
        )
      }
      SettingsType.SPACING_SETTINGS -> {
        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::FORCE_ONE_SPACE_BETWEEN_WORDS.name,
          MarkdownBundle.message("markdown.style.settings.spacing.between.words"),
          MarkdownBundle.message("markdown.style.settings.spacing.force.one.space")
        )

        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::FORCE_ONE_SPACE_AFTER_HEADER_SYMBOL.name,
          MarkdownBundle.message("markdown.style.settings.spacing.after.header.symbol"),
          MarkdownBundle.message("markdown.style.settings.spacing.force.one.space")
        )

        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::FORCE_ONE_SPACE_AFTER_LIST_BULLET.name,
          MarkdownBundle.message("markdown.style.settings.spacing.after.list.marker"),
          MarkdownBundle.message("markdown.style.settings.spacing.force.one.space")
        )

        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::FORCE_ONE_SPACE_AFTER_BLOCKQUOTE_SYMBOL.name,
          MarkdownBundle.message("markdown.style.settings.spacing.after.blockquote.marker"),
          MarkdownBundle.message("markdown.style.settings.spacing.force.one.space")
        )
      }
      else -> {}
    }
  }

  override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings? {
    return MarkdownCustomCodeStyleSettings(settings)
  }

  override fun getIndentOptionsEditor(): IndentOptionsEditor? = SmartIndentOptionsEditor()

  @org.intellij.lang.annotations.Language("Markdown")
  override fun getCodeSample(settingsType: SettingsType): String {
    return when (settingsType) {
      SettingsType.INDENT_SETTINGS -> {
        this::class.java.getResourceAsStream("indent_settings.md")
      }
      SettingsType.BLANK_LINES_SETTINGS -> {
        this::class.java.getResourceAsStream("blank_lines_settings.md")
      }
      SettingsType.SPACING_SETTINGS -> {
        this::class.java.getResourceAsStream("spacing_settings.md")
      }
      else -> {
        this::class.java.getResourceAsStream("default.md")
      }
    }.bufferedReader().use { it.readText() }
  }
}