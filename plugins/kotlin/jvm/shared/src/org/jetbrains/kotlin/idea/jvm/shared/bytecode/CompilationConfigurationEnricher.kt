// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.shared.bytecode

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.kotlin.config.CompilerConfiguration

/*
    Workaround to support K1 'STUB_UNBOUND_IR_SYMBOLS' compilation configuration parameter
 */
interface CompilationConfigurationEnricher {
    fun enrich(compilerConfiguration: CompilerConfiguration): CompilerConfiguration = compilerConfiguration

    companion object {
        private val EP_NAME = ExtensionPointName.create<CompilationConfigurationEnricher>("org.jetbrains.kotlin.idea.jvm.shared.bytecode.compilationConfigurationEnricher")

        @JvmStatic
        val single: CompilationConfigurationEnricher?
            get() {
                return EP_NAME.extensionList.singleOrNull()
            }
    }
}
