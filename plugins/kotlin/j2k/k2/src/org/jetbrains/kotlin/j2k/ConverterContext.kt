// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.j2k.externalCodeProcessing.NewExternalCodeProcessing
import org.jetbrains.kotlin.j2k.types.JKTypeFactory

class ConverterContext(
    val symbolProvider: JKSymbolProvider,
    val typeFactory: JKTypeFactory,
    val converter: JavaToKotlinConverter,
    val importStorage: JKImportStorage,
    val externalCodeProcessor: NewExternalCodeProcessing,
    val languageVersionSettings: LanguageVersionSettings,
    val settings: ConverterSettings
) {
    val project: Project
        get() = converter.project
}