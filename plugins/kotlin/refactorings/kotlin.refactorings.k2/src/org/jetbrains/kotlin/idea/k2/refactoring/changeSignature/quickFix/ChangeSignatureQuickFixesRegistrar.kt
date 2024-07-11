// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix

import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixRegistrar
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixesList
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KtQuickFixesListBuilder

class ChangeSignatureQuickFixesRegistrar: KotlinQuickFixRegistrar() {

    private val changeSignature = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(ChangeSignatureFixFactory.addParameterFactory)
        registerFactory(ChangeSignatureFixFactory.removeParameterFactory)
        registerFactory(ChangeSignatureFixFactory.typeMismatchFactory)
        registerFactory(ChangeSignatureFixFactory.nullForNotNullFactory)
        registerFactory(ReorderParametersFixFactory.unInitializedParameter)
        registerFactory(ChangeParameterTypeFixFactory.typeMismatchFactory)
        registerFactory(ChangeParameterTypeFixFactory.nullForNotNullTypeFactory)
    }

    override val list: KotlinQuickFixesList = changeSignature
}