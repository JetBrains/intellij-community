// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.k1.bytecode

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.analysis.api.KaNonPublicApi
import org.jetbrains.kotlin.analysis.api.descriptors.components.STUB_UNBOUND_IR_SYMBOLS
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.jvm.shared.bytecode.CompilationConfigurationEnricher

@K1Deprecation
@OptIn(KaNonPublicApi::class)
class K1CompilationConfigurationEnricher : CompilationConfigurationEnricher {
    override fun enrich(compilerConfiguration: CompilerConfiguration): CompilerConfiguration {
        return compilerConfiguration.copy()
            .apply {
                put(STUB_UNBOUND_IR_SYMBOLS, true)
            }
    }
}