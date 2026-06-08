// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.debugger.test

import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.mockJDI.MockVirtualMachine
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.debugger.core.FileApplicabilityChecker
import org.jetbrains.kotlin.idea.test.KotlinLightPlatformCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtPsiFactory

class K2FileApplicabilityCheckerTest : KotlinLightPlatformCodeInsightFixtureTestCase() {

    fun testGeneratedLambdaMethodDoesNotMatchTopLevelFunctionWithSamePrefix() = runBlocking {
        val actualText = """
            package sample

            fun actualOwner() {
                run {
                    val actual = 1 // LOCATION
                }
            }
        """.trimIndent()

        val psiFactory = KtPsiFactory(project)
        val actualFile = psiFactory.createFile("a.kt", actualText)
        val competingFile = psiFactory.createFile(
            "a.kt",
            """
                    package sample

                    fun generateAdaptedCallableReference() {
                        val competing = 1 // LOCATION
                    }
                """.trimIndent()
        )

        val locationIndex = actualText.lines().indexOfFirst { "LOCATION" in it }
        check(locationIndex >= 0) { "Marker '${"LOCATION"}' not found" }
        val lineNumber = locationIndex + 1

        val location = DebuggerUtilsEx.findOrCreateLocation(
            MockVirtualMachine(),
            "sample.AKt",
            "generateAdaptedCallableReference\$lambda\$0",
            lineNumber
        )

        assertSame(actualFile, FileApplicabilityChecker.chooseMostApplicableFile(listOf(actualFile, competingFile), location))
    }

}
