// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.fir.uast.test

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisApiInternals
import org.jetbrains.kotlin.analysis.low.level.api.fir.project.structure.LLFirBuiltinsSessionFactory
import org.jetbrains.kotlin.analysis.providers.KotlinGlobalModificationService

@OptIn(KtAnalysisApiInternals::class)
internal fun Project.invalidateAllCachesForUastTests() {
    runWriteAction {
        KotlinGlobalModificationService.getInstance(this).publishGlobalModuleStateModification()
    }
    service<LLFirBuiltinsSessionFactory>().clearForTheNextTest()
}