// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename.handlers

import com.intellij.openapi.util.NlsContexts.DialogMessage
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolOrigin
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

internal class RenameSyntheticDeclarationByReferenceHandler : AbstractForbidRenamingSymbolByReferenceHandler() {
    context(KtAnalysisSession)
    override fun shouldForbidRenaming(symbol: KtSymbol): Boolean {
        return symbol.origin == KtSymbolOrigin.SOURCE_MEMBER_GENERATED && !(symbol is KtConstructorSymbol && symbol.isPrimary)
    }

    override fun getErrorMessage(): @DialogMessage String {
        return KotlinBundle.message("text.rename.is.not.applicable.to.synthetic.declarations")
    }
}
