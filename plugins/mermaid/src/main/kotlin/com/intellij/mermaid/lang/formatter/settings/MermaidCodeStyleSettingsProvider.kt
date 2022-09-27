package com.intellij.mermaid.lang.formatter.settings

import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.*

internal class MermaidCodeStyleSettingsProvider  : LanguageCodeStyleSettingsProvider() {
  override fun getLanguage(): Language = MermaidLanguage

  override fun createConfigurable(baseSettings: CodeStyleSettings, modelSettings: CodeStyleSettings): CodeStyleConfigurable {
    return MermaidCodeStyleConfigurable(baseSettings, modelSettings)
  }

  override fun getConfigurableDisplayName() = MermaidBundle.message("mermaid.settings.name")

  override fun customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) {
    when (settingsType) {
      SettingsType.BLANK_LINES_SETTINGS -> {
//        consumer.showCustomOption(
//          MermaidCustomCodeStyleSettings::class.java,
//          MermaidCustomCodeStyleSettings::.name,
//          MermaidBundle.message("mermaid.style.settings."),
//          CodeStyleSettingsCustomizableOptions.getInstance().BLANK_LINES
//        )
      }
      SettingsType.SPACING_SETTINGS -> {
        consumer.showCustomOption(
          MermaidCustomCodeStyleSettings::class.java,
          MermaidCustomCodeStyleSettings::FORCE_ONE_SPACE_BETWEEN_WORDS.name,
          MermaidBundle.message("mermaid.style.settings.spacing.between.words"),
          MermaidBundle.message("mermaid.style.settings.spacing.force.one.space")
        )
      }
      else -> {}
    }
  }

  override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings {
    return MermaidCustomCodeStyleSettings(settings)
  }

  override fun getIndentOptionsEditor(): IndentOptionsEditor = SmartIndentOptionsEditor()

  @org.intellij.lang.annotations.Language("MyMermaid")
  override fun getCodeSample(settingsType: SettingsType): String {
    val sampleName = when (settingsType) {
      SettingsType.INDENT_SETTINGS -> "indent_settings.mymermaid"
      SettingsType.BLANK_LINES_SETTINGS -> "default.mymermaid"
      SettingsType.SPACING_SETTINGS -> "default.mymermaid"
      else -> "default.mymermaid"
    }
    val codeSample = this::class.java.getResourceAsStream(sampleName)?.bufferedReader()?.use { it.readText() }
    return codeSample ?: "Failed to get predefined code sample"
  }
}
