// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.editing

import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomDataSynchronizer
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.formatter.YAMLCodeStyleSettings

//todo split on two separate classes for the backend and the frontend
@InternalIgnoreDependencyViolation
class YamlCodeStyleSettingsCustomDataSynchronizer : CodeStyleSettingsCustomDataSynchronizer<YAMLCodeStyleSettings>() {
    override val language: YAMLLanguage get() = YAMLLanguage.INSTANCE

    override val customCodeStyleSettingsClass get() = YAMLCodeStyleSettings::class.java
}