// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core

import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.resolve.diagnostics.DiagnosticSuppressor

class DiagnosticSuppressorForDebugger : DiagnosticSuppressor {
    override fun isSuppressed(diagnostic: Diagnostic): Boolean {
        val element = diagnostic.psiElement
        val containingFile = element.containingFile

        if (containingFile is KtCodeFragment) {
            val diagnosticFactory = diagnostic.factory
            return diagnosticFactory == Errors.UNSAFE_CALL
        }

        return false
    }
}