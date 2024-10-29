// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.base.util

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtElement

@ApiStatus.Internal
fun <T> runSmartAnalyze(useSiteElement: KtElement, action: KaSession.() -> T): T =
    ReadAction.nonBlocking<T> { analyze(useSiteElement, action) }
        .inSmartMode(runReadAction { useSiteElement.project })
        .executeSynchronously()

@ApiStatus.Internal
suspend fun <T> smartAnalyze(useSiteElement: KtElement, action: KaSession.() -> T): T =
    smartReadAction(readAction { useSiteElement.project }) {
        analyze(useSiteElement, action)
    }

@ApiStatus.Internal
fun <T> runDumbAnalyze(useSiteElement: KtElement, fallback: T, action: KaSession.() -> T): T = ReadAction.nonBlocking<T> {
    if (DumbService.isDumb(useSiteElement.project)) return@nonBlocking fallback
    try {
        analyze(useSiteElement, action)
    } catch (_: IndexNotReadyException) {
        fallback
    }
}.executeSynchronously()

@ApiStatus.Internal
inline fun <T> dumbAction(project: Project, fallback: T, action: () -> T): T {
    if (DumbService.isDumb(project)) return fallback
    return try {
        action()
    } catch (_: IndexNotReadyException) {
        fallback
    }
}

@ApiStatus.Internal
suspend fun <T> dumbAnalyze(useSiteElement: KtElement, fallback: T, action: KaSession.() -> T): T =
    dumbAction(readAction { useSiteElement.project }, fallback) {
        readAction { analyze(useSiteElement, action) }
    }
