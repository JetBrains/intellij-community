// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.lang.JavaVersion
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.caches.project.LibraryModificationTracker
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.KotlinRoot
import java.io.File
import java.util.*

@JvmField
val IDEA_TEST_DATA_DIR = File(KotlinRoot.DIR, "idea/tests/testData")

fun KtFile.dumpTextWithErrors(ignoreErrors: Set<DiagnosticFactory<*>> = emptySet()): String {
    val text = text
    if (InTextDirectivesUtils.isDirectiveDefined(text, "// DISABLE-ERRORS")) return text
    val diagnostics = analyzeWithContent().diagnostics
    val errors = diagnostics.filter { diagnostic ->
        diagnostic.severity == Severity.ERROR && diagnostic.factory !in ignoreErrors
    }
    if (errors.isEmpty()) return text
    val header = errors.joinToString("\n", postfix = "\n") { "// ERROR: " + DefaultErrorMessages.render(it).replace('\n', ' ') }
    return header + text
}

fun closeAndDeleteProject() = LightPlatformTestCase.closeAndDeleteProject()

fun invalidateLibraryCache(project: Project) {
    LibraryModificationTracker.getInstance(project).incModificationCount()
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
