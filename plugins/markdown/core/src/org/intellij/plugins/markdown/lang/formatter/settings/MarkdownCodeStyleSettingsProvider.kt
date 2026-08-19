// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter.settings

import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.CodeStyleConfigurable
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizableOptions
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import javax.swing.JCheckBox

internal class MarkdownCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
  override fun getLanguage(): Language = MarkdownLanguage.INSTANCE

  override fun createConfigurable(baseSettings: CodeStyleSettings, modelSettings: CodeStyleSettings): CodeStyleConfigurable {
    return MarkdownCodeStyleConfigurable(baseSettings, modelSettings)
  }

  override fun getConfigurableDisplayName() = MarkdownBundle.message("markdown.settings.name")

  override fun customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) {
    when (settingsType) {
      SettingsType.INDENT_SETTINGS -> {
        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::USE_FIXED_INDENTS_FOR_SUBLISTS.name,
          MarkdownBundle.message("markdown.style.settings.use.fixed.indents.for.sublists"),
          null
        )
      }
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
        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::FORMAT_TABLES.name,
          MarkdownBundle.message("markdown.style.settings.format.tables"),
          MarkdownBundle.message("markdown.style.settings.group.when.reformatting")
        )
        consumer.showCustomOption(
          MarkdownCustomCodeStyleSettings::class.java,
          MarkdownCustomCodeStyleSettings::TABLE_STYLE.name,
          MarkdownBundle.message("markdown.style.settings.table.style"),
          MarkdownBundle.message("markdown.style.settings.group.when.reformatting"),
          TableStyle.entries.map { MarkdownBundle.message(it.messageKey) }.toTypedArray(),
          TableStyle.entries.map { it.ordinal }.toIntArray()
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
      else -> {}
    }
  }

  override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings {
    return MarkdownCustomCodeStyleSettings(settings)
  }

  override fun getIndentOptionsEditor(): IndentOptionsEditor = MarkdownIndentOptionsEditor(this)

  private class MarkdownIndentOptionsEditor(provider: MarkdownCodeStyleSettingsProvider) : SmartIndentOptionsEditor(provider) {
    private lateinit var useFixedIndentsForSublists: JCheckBox

    override fun addComponents() {
      super.addComponents()
      useFixedIndentsForSublists = JCheckBox(MarkdownBundle.message("markdown.style.settings.use.fixed.indents.for.sublists"))
      add(useFixedIndentsForSublists)
    }

    override fun isModified(settings: CodeStyleSettings, options: CommonCodeStyleSettings.IndentOptions): Boolean {
      val customSettings = settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java)
      return super.isModified(settings, options) ||
             useFixedIndentsForSublists.isSelected != customSettings.USE_FIXED_INDENTS_FOR_SUBLISTS
    }

    override fun apply(settings: CodeStyleSettings, options: CommonCodeStyleSettings.IndentOptions) {
      super.apply(settings, options)
      settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).USE_FIXED_INDENTS_FOR_SUBLISTS =
        useFixedIndentsForSublists.isSelected
    }

    override fun reset(settings: CodeStyleSettings, options: CommonCodeStyleSettings.IndentOptions) {
      super.reset(settings, options)
      useFixedIndentsForSublists.isSelected =
        settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).USE_FIXED_INDENTS_FOR_SUBLISTS
    }

    override fun setEnabled(enabled: Boolean) {
      super.setEnabled(enabled)
      useFixedIndentsForSublists.isEnabled = enabled
    }
  }

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
