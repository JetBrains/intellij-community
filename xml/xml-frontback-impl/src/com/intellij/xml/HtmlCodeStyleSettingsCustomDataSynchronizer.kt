// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml

import com.intellij.lang.html.HTMLLanguage
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomDataSynchronizer
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings

class HtmlCodeStyleSettingsCustomDataSynchronizer : CodeStyleSettingsCustomDataSynchronizer<HtmlCodeStyleSettings>() {
  override val language: HTMLLanguage get() = HTMLLanguage.INSTANCE

  override val customCodeStyleSettingsClass: Class<HtmlCodeStyleSettings> get() = HtmlCodeStyleSettings::class.java
}