// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter

import com.intellij.lang.Language
import com.intellij.psi.codeStyle.CodeStyleConfigurable
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import org.intellij.plugins.markdown.lang.MarkdownLanguage

class MarkdownLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
  override fun getLanguage(): Language = MarkdownLanguage.INSTANCE

  private val STANDARD_WRAPPING_OPTIONS = arrayOf("RIGHT_MARGIN", "WRAP_ON_TYPING")

  override fun createConfigurable(baseSettings: CodeStyleSettings, modelSettings: CodeStyleSettings):
    CodeStyleConfigurable = MarkdownCodeStyleConfigurable(baseSettings, modelSettings)

  override fun getConfigurableDisplayName() = "Markdown"

  override fun customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType)  {
    if (settingsType == LanguageCodeStyleSettingsProvider.SettingsType.WRAPPING_AND_BRACES_SETTINGS) {
      consumer.showStandardOptions(*STANDARD_WRAPPING_OPTIONS)
    }
  }

  override fun getCodeSample(settingsType: SettingsType): String =
    """**Markdown parser and generator written in Kotlin**

Introduction
-------------

[intellij-markdown][self] is a fast and extensible markdown processor.
It is aimed to suit the following needs:
- Use one code base for both client and server-side processing;
- Support different flavours;
- Be easily extensible.

Since the parser is written in [Kotlin], it can be compiled to both JS and Java bytecode
thus can be used everywhere.

Usage
-----

One of the goals of this project is to provide flexibility in terms of the tasks being solved.
[Markdown plugin] for JetBrains IDEs is an example of usage when markdown processing is done
in several stages:

* Parse block structure without parsing inlines to provide lazy parsable blocks for IDE;
* Quickly parse inlines of a given block to provide faster syntax highlighting update;
* Generate HTML for preview.

These tasks may be completed independently according to the current needs.

#### Simple html generation (Kotlin)

```kotlin
val src = "Some *Markdown*"
val flavour = CommonMarkFlavourDescriptor()
val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(src)
val html = HtmlGenerator(src, parsedTree, flavour).generateHtml()
```
    """.trimMargin()
}