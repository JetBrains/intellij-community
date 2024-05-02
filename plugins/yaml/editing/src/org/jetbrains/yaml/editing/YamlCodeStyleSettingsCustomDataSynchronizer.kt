// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.editing

import com.intellij.psi.codeStyle.CodeStyleSettingsCustomDataSynchronizer
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.formatter.YAMLCodeStyleSettings

class YamlCodeStyleSettingsCustomDataSynchronizer : CodeStyleSettingsCustomDataSynchronizer<YAMLCodeStyleSettings>() {
    override val language: YAMLLanguage get() = YAMLLanguage.INSTANCE

    override val customCodeStyleSettingsClass get() = YAMLCodeStyleSettings::class.java
}