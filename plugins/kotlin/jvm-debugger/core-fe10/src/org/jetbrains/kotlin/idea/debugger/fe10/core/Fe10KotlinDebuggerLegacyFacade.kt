// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.fe10.core

import org.jetbrains.kotlin.idea.debugger.FileRankingCalculatorForIde
import org.jetbrains.kotlin.idea.debugger.base.util.KotlinFileSelector
import org.jetbrains.kotlin.idea.debugger.core.KotlinDebuggerLegacyFacade
import org.jetbrains.kotlin.idea.debugger.core.KotlinEditorTextProvider
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.ActualDeclarationProvider

internal class Fe10KotlinDebuggerLegacyFacade : KotlinDebuggerLegacyFacade {
    override val editorTextProvider: KotlinEditorTextProvider
        get() = LegacyKotlinEditorTextProvider

    override val fileSelector: KotlinFileSelector
        get() = FileRankingCalculatorForIde

    override val actualDeclarationProvider: ActualDeclarationProvider
        get() = Fe10ActualDeclarationProvider
}
