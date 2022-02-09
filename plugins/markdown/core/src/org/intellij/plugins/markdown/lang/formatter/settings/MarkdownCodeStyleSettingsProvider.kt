// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    @Suppress("NON_EXHAUSTIVE_WHEN")
    when (settingsType) {
      SettingsType.WRAPPING_AND_BRACES_SETTINGS -> {
        consumer.showStandardOptions("RIGHT_MARGIN", "WRAP_ON_TYPING")
        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::WRAP_TEXT_IF_LONG.name,
          MarkdownBundle.message("markdown.style.settings.text.wrapping"),
          null,
          CodeStyleSettingsCustomizable.OptionAnchor.AFTER,
          "WRAP_ON_TYPING"
        )
        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::WRAP_TEXT_INSIDE_BLOCKQUOTES.name,
          MarkdownBundle.message("markdown.style.settings.text.wrapping.inside.blockquotes"),
          null,
          CodeStyleSettingsCustomizable.OptionAnchor.AFTER,
          "WRAP_ON_TYPING"
        )
        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::KEEP_LINE_BREAKS_INSIDE_TEXT_BLOCKS.name,
          MarkdownBundle.message("markdown.style.settings.line.breaks.inside.text.blocks"),
          MarkdownBundle.message("markdown.style.settings.group.when.reformatting")
        )
        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::INSERT_QUOTE_ARROWS_ON_WRAP.name,
          MarkdownBundle.message("markdown.style.settings.insert.quote.arrows"),
          MarkdownBundle.message("markdown.style.settings.group.when.reformatting")
        )
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
    }
  }

  override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings {
    return MarkdownCustomCodeStyleSettings(settings)
  }

  override fun getIndentOptionsEditor(): IndentOptionsEditor = SmartIndentOptionsEditor()

  @org.intellij.lang.annotations.Language("Markdown")
  override fun getCodeSample(settingsType: SettingsType): String {
    val sampleName = when (settingsType) {
      SettingsType.INDENT_SETTINGS -> "indent_settings.md"
      SettingsType.BLANK_LINES_SETTINGS -> "blank_lines_settings.md"
      SettingsType.SPACING_SETTINGS -> "spacing_settings.md"
      else -> "default.md"
    }
    val codeSample = this::class.java.getResourceAsStream(sampleName)?.bufferedReader()?.use { it.readText() }
    return codeSample ?: "Failed to get predefined code sample"
  }
}
