// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml

import com.intellij.lang.Language
import com.intellij.lang.xml.XMLLanguage
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomDataSynchronizer
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings

class XmlCodeStyleSettingsCustomDataSynchronizer : CodeStyleSettingsCustomDataSynchronizer<XmlCodeStyleSettings>() {
  override val language: Language
    get() = XMLLanguage.INSTANCE

  override val customCodeStyleSettingsClass: Class<XmlCodeStyleSettings>
    get() = XmlCodeStyleSettings::class.java
}