// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.sun.jdi.Location
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.debugger.FileRankingCalculator
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractK1FileRankingTest : AbstractFileRankingTest() {
    override fun createDebuggerTestCompilerFacility(
        testFiles: TestFiles, jvmTarget: JvmTarget, compileConfiguration: TestCompileConfiguration,
    ) = K1DebuggerTestCompilerFacility(project, testFiles, jvmTarget, compileConfiguration)

    override fun rankFiles(
        compilerFacility: DebuggerTestCompilerFacility,
        sourceFiles: List<KtFile>,
        options: Set<String>
    ): (List<KtFile>, Location) -> Map<KtFile, Int> {
        val (_, analysisResult) = K1DebuggerTestCompilerFacility.analyzeSources(project, sourceFiles)
        val bindingContext = analysisResult.bindingContext
        val doNotCheckClassFqName = "DO_NOT_CHECK_CLASS_FQNAME" in options
        val calculator = object : FileRankingCalculator(checkClassFqName = !doNotCheckClassFqName) {
            override fun analyze(element: KtElement) = bindingContext
        }
        val ranker: (List<KtFile>, Location) -> Map<KtFile, Int> = { allFilesWithSameName, location ->
            runBlocking {
                calculator.rankFiles(allFilesWithSameName, location)
            }
        }
        return ranker
    }
}