// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.extractFunction

import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionGeneratorOptions
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IExtractionGeneratorConfiguration

data class ExtractionGeneratorConfiguration(
    override val descriptor: ExtractableCodeDescriptor,
    override val generatorOptions: ExtractionGeneratorOptions
) : IExtractionGeneratorConfiguration<KaType>