// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix

import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixRegistrar
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixesList
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KtQuickFixesListBuilder

class ChangeSignatureQuickFixesRegistrar: KotlinQuickFixRegistrar() {
    private val changeSignature = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerApplicator(ChangeSignatureFixFactory.addParameterFactory)
        registerApplicator(ChangeSignatureFixFactory.removeParameterFactory)
        registerApplicator(ChangeSignatureFixFactory.typeMismatchFactory)
        registerApplicator(ChangeSignatureFixFactory.nullForNotNullFactory)
    }

    override val list: KotlinQuickFixesList = changeSignature
}