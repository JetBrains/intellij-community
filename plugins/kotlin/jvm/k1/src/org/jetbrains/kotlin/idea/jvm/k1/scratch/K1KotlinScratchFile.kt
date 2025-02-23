// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.k1.scratch

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchExecutor
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFile
import org.jetbrains.kotlin.resolve.AnalyzingUtils
import java.util.concurrent.Callable

class K1KotlinScratchFile(project: Project, file: VirtualFile) : ScratchFile(project, file) {
    var replScratchExecutor: SequentialScratchExecutor? = null
    var compilingScratchExecutor: ScratchExecutor? = null

    @RequiresBackgroundThread
    fun hasErrors(): Boolean {
        val psiFile = ktFile ?: return false

        return ReadAction
            .nonBlocking(Callable {
                try {
                    AnalyzingUtils.checkForSyntacticErrors(psiFile)
                } catch (e: IllegalArgumentException) {
                    return@Callable true
                }

                return@Callable psiFile.analyzeWithContent().diagnostics.any { it.severity == Severity.ERROR }
            })
            .inSmartMode(project)
            .expireWith(KotlinPluginDisposable.Companion.getInstance(project))
            .expireWhen { project.isDisposed() }
            .executeSynchronously()
    }
}