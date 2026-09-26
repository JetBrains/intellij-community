// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiElement
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import org.jetbrains.kotlin.findUsages.AbstractFindUsagesTest
import org.jetbrains.kotlin.findUsages.KotlinFindUsageConfigurator
import org.jetbrains.kotlin.idea.test.Diagnostic
import org.jetbrains.kotlin.idea.test.TestMetadataUtil
import org.jetbrains.kotlin.idea.test.kmp.KMPTestPlatform
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractFindUsagesWithCompilerReferenceIndexTest : KotlinCompilerReferenceTestBase() {
    override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
        super.tuneFixture(moduleBuilder)
        moduleBuilder.setLanguageLevel(LanguageLevel.JDK_17)
        moduleBuilder.addJdkVersion(LanguageLevel.JDK_1_8)
    }

    protected open val ignoreLog: Boolean get() = false

    override fun getTestDataPath(): String = File(TestMetadataUtil.getTestDataPath(javaClass)).path

    abstract fun getDiagnosticProvider(): (KtFile) -> List<Diagnostic>

    protected fun doTest(path: String) {
        val criType = AbstractFindUsagesTest.Companion.FindUsageTestType.FIR_CRI
        runCatching {
            AbstractFindUsagesTest.Companion.doFindUsageTest<PsiElement>(
                path,
                configurator = KotlinFindUsageConfigurator.Companion.fromFixture(myFixture),
                testType = criType,
                ignoreLog = ignoreLog,
                testPlatform = KMPTestPlatform.Unspecified,
                executionWrapper = { findUsageTest ->
                    findUsageTest(AbstractFindUsagesTest.Companion.FindUsageTestType.FIR)

                    installCompiler()
                    rebuildProject()
                    findUsageTest(criType)
                },
                diagnosticProvider = getDiagnosticProvider(),
            )
        }.fold(
            onSuccess = {},
            onFailure = {
                if (isCompatibleVersions) {
                    throw it
                }
            },
        )
    }

}