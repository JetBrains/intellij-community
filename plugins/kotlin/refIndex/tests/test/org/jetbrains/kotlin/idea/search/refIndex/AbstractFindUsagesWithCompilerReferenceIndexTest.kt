// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiElement
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import org.jetbrains.kotlin.findUsages.AbstractFindUsagesTest
import org.jetbrains.kotlin.findUsages.AbstractFindUsagesTest.Companion.FindUsageTestType
import org.jetbrains.kotlin.findUsages.KotlinFindUsageConfigurator
import org.jetbrains.kotlin.idea.test.TestMetadataUtil
import java.io.File

abstract class AbstractFindUsagesWithCompilerReferenceIndexTest : KotlinCompilerReferenceTestBase() {
    override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
        super.tuneFixture(moduleBuilder)
        moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8)
        moduleBuilder.addJdk(IdeaTestUtil.getMockJdk18Path().path)
    }

    override fun getTestDataPath(): String = File(TestMetadataUtil.getTestDataPath(javaClass)).path

    protected fun doTest(path: String): Unit = AbstractFindUsagesTest.doFindUsageTest<PsiElement>(
        path,
        configurator = KotlinFindUsageConfigurator.fromFixture(myFixture),
        executionWrapper = { findUsageTest ->
            findUsageTest(FindUsageTestType.DEFAULT)

            installCompiler()
            rebuildProject()
            findUsageTest(FindUsageTestType.CRI)
        },
        testType = FindUsageTestType.CRI,
    )
}
