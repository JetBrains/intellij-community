// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFile

val J2K_PROJECT_DESCRIPTOR: KotlinWithJdkAndRuntimeLightProjectDescriptor =
    object : KotlinWithJdkAndRuntimeLightProjectDescriptor() {
        override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk21()
    }

internal val J2K_FULL_JDK_PROJECT_DESCRIPTOR: KotlinWithJdkAndRuntimeLightProjectDescriptor =
    KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

// TODO: adapted from `org.jetbrains.kotlin.idea.test.TestUtilsKt.dumpTextWithErrors`
@OptIn(KtAllowAnalysisOnEdt::class)
internal fun getK2FileTextWithErrors(file: KtFile): String {
    val errors: List<String> = allowAnalysisOnEdt {
        analyze(file) {
            val diagnostics = file.collectDiagnosticsForFile(filter = ONLY_COMMON_CHECKERS).asSequence()
            diagnostics
                // TODO: For some reason, there is a "redeclaration" error on every declaration for K2 tests
                .filter { it.factoryName != "PACKAGE_OR_CLASSIFIER_REDECLARATION" }
                .filter { it.severity == Severity.ERROR }
                .map { it.defaultMessage.replace(oldChar = '\n', newChar = ' ') }
                .toList()
        }
    }

    if (errors.isEmpty()) return file.text
    val header = errors.joinToString(separator = "\n", postfix = "\n") { "// ERROR: $it" }
    return header + file.text
}