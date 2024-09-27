// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.fir.uast.test

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinGlobalModificationService
import org.jetbrains.kotlin.analysis.low.level.api.fir.LLFirInternals
import org.jetbrains.kotlin.analysis.low.level.api.fir.projectStructure.LLFirBuiltinsSessionFactory

@OptIn(LLFirInternals::class)
fun Project.invalidateAllCachesForUastTests() {
    runWriteAction {
        KotlinGlobalModificationService.getInstance(this).publishGlobalModuleStateModification()
    }
    service<LLFirBuiltinsSessionFactory>().clearForTheNextTest()
}
