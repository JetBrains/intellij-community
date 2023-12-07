// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test

import com.intellij.facet.FacetManager
import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.util.indexing.IndexingFlag
import com.intellij.util.indexing.UnindexedFilesScanner
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.facet.KotlinFacetConfiguration
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

@JvmField
val IDEA_TEST_DATA_DIR = File(KotlinRoot.DIR, "idea/tests/testData")

fun KtFile.dumpTextWithErrors(ignoreErrors: Set<DiagnosticFactory<*>> = emptySet()): String {
    val text = text
    if (InTextDirectivesUtils.isDirectiveDefined(text, "// DISABLE-ERRORS")) return text
    val diagnostics = kotlin.run {
        var lastException: Exception? = null
        for (attempt in 0 until 2) {
            try {
                analyzeWithContent().diagnostics.let {
                    return@run it
                }
            } catch (e: Exception) {
                if (e is ControlFlowException) {
                    lastException = e.cause as? Exception ?: e
                    continue
                }
                lastException = e
            }
        }
        throw lastException ?: IllegalStateException()
    }
    val errors = diagnostics.filter { diagnostic ->
        diagnostic.severity == Severity.ERROR && diagnostic.factory !in ignoreErrors
    }
    if (errors.isEmpty()) return text
    val header = errors.joinToString("\n", postfix = "\n") { "// ERROR: " + DefaultErrorMessages.render(it).replace('\n', ' ') }
    return header + text
}

fun JavaCodeInsightTestFixture.dumpErrorLines(): List<String> {
    if (InTextDirectivesUtils.isDirectiveDefined(file.text, "// DISABLE-ERRORS")) return emptyList()
    return doHighlighting().filter { it.severity == HighlightSeverity.ERROR }.map {
        "// ERROR: ${it.description.replace('\n', ' ')}"
    }
}

fun Project.waitIndexingComplete(indexingReason: String? = null) {
    val project = this
    UIUtil.dispatchAllInvocationEvents()
    invokeAndWaitIfNeeded {
        // TODO: [VD] a dirty hack to reindex created android project
        IndexingFlag.cleanupProcessedFlag("org.jetbrains.kotlin.idea.test.TestUtilsKt.waitIndexingComplete")
        with(DumbService.getInstance(project)) {
            UnindexedFilesScanner(project, indexingReason).queue()
            completeJustSubmittedTasks()
        }
        UIUtil.dispatchAllInvocationEvents()
    }
}

fun closeAndDeleteProject() = LightPlatformTestCase.closeAndDeleteProject()

fun invalidateLibraryCache(project: Project) {
    JavaLibraryModificationTracker.incModificationCount(project)
}

fun Document.extractMarkerOffset(project: Project, caretMarker: String = "<caret>"): Int {
    return extractMultipleMarkerOffsets(project, caretMarker).singleOrNull() ?: -1
}

fun Document.extractMultipleMarkerOffsets(project: Project, caretMarker: String = "<caret>"): List<Int> {
    val offsets = ArrayList<Int>()

    runWriteAction {
        val text = StringBuilder(text)
        while (true) {
            val offset = text.indexOf(caretMarker)
            if (offset >= 0) {
                text.delete(offset, offset + caretMarker.length)
                setText(text.toString())

                offsets += offset
            } else {
                break
            }
        }
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments()
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(this)

    return offsets
}

fun Module.setupKotlinFacet(configure: KotlinFacetConfiguration.() -> Unit) = apply {
    runWriteAction {
        val facet = FacetManager.getInstance(this).addFacet(KotlinFacetType.INSTANCE, KotlinFacetType.NAME, null)
        val configuration = facet.configuration

        // this is actually needed so facet settings object is in a valid state
        configuration.settings.compilerArguments = K2JVMCompilerArguments()
        // make sure module-specific settings are used
        configuration.settings.useProjectSettings = false

        configuration.configure()
    }
}