// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.k1.quickfix

import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors.*
import org.jetbrains.kotlin.idea.jvm.k1.inspections.AddReflectionQuickFix
import org.jetbrains.kotlin.idea.jvm.k1.inspections.AddScriptRuntimeQuickFix
import org.jetbrains.kotlin.idea.jvm.k1.inspections.AddTestLibQuickFix
import org.jetbrains.kotlin.idea.quickfix.KotlinIntentionActionsFactory
import org.jetbrains.kotlin.idea.quickfix.QuickFixContributor
import org.jetbrains.kotlin.idea.quickfix.QuickFixes
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm.NO_REFLECTION_IN_CLASS_PATH

class JvmQuickFixRegistrar : QuickFixContributor {
    override fun registerQuickFixes(quickFixes: QuickFixes) {
        fun DiagnosticFactory<*>.registerFactory(vararg factory: KotlinIntentionActionsFactory) {
            quickFixes.register(this, *factory)
        }

        UNRESOLVED_REFERENCE.registerFactory(AddTestLibQuickFix)

        UNSUPPORTED_FEATURE.registerFactory(EnableUnsupportedFeatureFix)

        NO_REFLECTION_IN_CLASS_PATH.registerFactory(AddReflectionQuickFix)

        MISSING_SCRIPT_STANDARD_TEMPLATE.registerFactory(AddScriptRuntimeQuickFix)
    }
}
