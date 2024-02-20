// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.k2

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter.EXTENDED_AND_COMMON_CHECKERS
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.diagnostics.Severity.ERROR
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.j2k.AbstractJavaToKotlinConverterSingleFileTest
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.J2kConverterExtension
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K2
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractK2JavaToKotlinConverterSingleFileTest : AbstractJavaToKotlinConverterSingleFileTest() {
    override fun isFirPlugin(): Boolean = true

    override fun tearDown() {
        runAll(
            ThrowableRunnable { project.invalidateCaches() },
            ThrowableRunnable { super.tearDown() }
        )
    }

    override fun fileToKotlin(text: String, settings: ConverterSettings): String {
        val file = createJavaFile(text)
        val extension = J2kConverterExtension.extension(K2)
        val converter = extension.createJavaToKotlinConverter(project, module, settings)
        val postProcessor = extension.createPostProcessor()
        return converter.filesToKotlin(listOf(file), postProcessor).results.single()
    }

    // TODO: adapted from `org.jetbrains.kotlin.idea.test.TestUtilsKt.dumpTextWithErrors`
    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun dumpTextWithErrors(file: KtFile): String {
        val text = file.text
        if (InTextDirectivesUtils.isDirectiveDefined(text, "// DISABLE-ERRORS")) return text

        val errors = run {
            var lastException: Exception? = null
            for (attempt in 0 until 2) {
                try {
                    allowAnalysisOnEdt {
                        analyze(file) {
                            val diagnostics = file.collectDiagnosticsForFile(filter = EXTENDED_AND_COMMON_CHECKERS).asSequence()
                            return@run diagnostics
                                // TODO: For some reason, there is a "redeclaration" error on every declaration for K2 tests
                                .filter { it.factoryName != "PACKAGE_OR_CLASSIFIER_REDECLARATION" }
                                .filter { it.severity == ERROR }
                                .map { it.defaultMessage.replace('\n', ' ') }
                                .toList()
                        }
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

        if (errors.isEmpty()) return text
        val header = errors.joinToString(separator = "\n", postfix = "\n") { "// ERROR: $it" }
        return header + text
    }
}