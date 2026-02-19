// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename.handlers

import com.intellij.openapi.util.NlsContexts.DialogMessage
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

internal class RenameCompilerPluginDeclarationByReferenceHandler : AbstractForbidRenamingSymbolByReferenceHandler() {
    context(_: KaSession)
    override fun shouldForbidRenaming(symbol: KaSymbol): Boolean {
        return symbol.origin == KaSymbolOrigin.PLUGIN
    }

    override fun getErrorMessage(): @DialogMessage String {
        return KotlinBundle.message("text.rename.is.not.applicable.to.compiler.plugin.generated.declarations")
    }
}
