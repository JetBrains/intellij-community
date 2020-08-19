// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter.settings

import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.*
import org.intellij.plugins.markdown.lang.MarkdownLanguage

internal class MarkdownCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
  override fun getLanguage(): Language = MarkdownLanguage.INSTANCE

  override fun createConfigurable(baseSettings: CodeStyleSettings, modelSettings: CodeStyleSettings): CodeStyleConfigurable {
    return MarkdownCodeStyleConfigurable(baseSettings, modelSettings)
  }

  override fun getConfigurableDisplayName() = "Markdown"

  override fun customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) {
    when (settingsType) {
      SettingsType.WRAPPING_AND_BRACES_SETTINGS -> {
        consumer.showStandardOptions("RIGHT_MARGIN", "WRAP_ON_TYPING")
      }
      SettingsType.BLANK_LINES_SETTINGS -> {
        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::MAX_LINES_AROUND_HEADER.name, "Around header",
          CodeStyleSettingsCustomizable.BLANK_LINES
        )

        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::MAX_LINES_AROUND_BLOCK_ELEMENTS.name, "Around block elements",
          CodeStyleSettingsCustomizable.BLANK_LINES
        )

        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::MAX_LINES_BETWEEN_PARAGRAPHS.name, "Between paragraphs",
          CodeStyleSettingsCustomizable.BLANK_LINES
        )

        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::MIN_LINES_AROUND_HEADER.name, "Around header",
          CodeStyleSettingsCustomizable.BLANK_LINES_KEEP
        )

        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::MIN_LINES_AROUND_BLOCK_ELEMENTS.name, "Around block elements",
          CodeStyleSettingsCustomizable.BLANK_LINES_KEEP
        )

        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::MIN_LINES_BETWEEN_PARAGRAPHS.name, "Between paragraphs",
          CodeStyleSettingsCustomizable.BLANK_LINES_KEEP
        )
      }
      SettingsType.SPACING_SETTINGS -> {
        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::FORCE_ONE_SPACE_BETWEEN_WORDS.name, "Between words",
          "Force One Space"
        )

        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::FORCE_ONE_SPACE_AFTER_HEADER_SYMBOL.name, "After header symbol",
          "Force One Space"
        )

        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::FORCE_ONE_SPACE_AFTER_LIST_BULLET.name, "After list marker",
          "Force One Space"
        )

        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::FORCE_ONE_SPACE_AFTER_BLOCKQUOTE_SYMBOL.name, "After blockquote marker",
          "Force One Space"
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