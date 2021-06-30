// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.psi.CommonClassNames
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifactNames
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts

@SkipSlowTestLocally
class KotlinCompilerReferenceTest : KotlinCompilerReferenceTestBase() {
    override fun setUp() {
        super.setUp()
        installCompiler()
        myFixture.testDataPath = getTestDataPath("compilerIndex") + name
    }

    fun testIsNotReady() {
        myFixture.configureByFile("Main.kt")
        assertIndexNotReady()
    }

    fun testFindItself() {
        myFixture.configureByFile("Main.kt")
        rebuildProject()
        assertUsageInMainFile()

        addFileAndAssertIndexNotReady()

        rebuildProject()
        assertUsageInMainFile()

        myFixture.addFileToProject("Another.groovy", "")
        assertUsageInMainFile()

        addFileAndAssertIndexNotReady("JavaClass.java")
    }

    fun testSimpleUsageInFullyCompiledProject() {
        myFixture.configureByFiles("Main.kt", "Foo.kt", "Boo.kt", "Doo.kt")
        rebuildProject()
        TestCase.assertEquals(setOf("Main.kt", "Foo.kt", "Boo.kt"), getReferentFilesForElementUnderCaret())
        addFileAndAssertIndexNotReady()
    }

    fun testSimpleJavaLibraryClass() {
        myFixture.configureByFiles("Main.kt", "Boo.kt")
        rebuildProject()
        TestCase.assertEquals(setOf("Main.kt"), getReferentFiles(myFixture.findClass(CommonClassNames.JAVA_UTIL_ARRAY_LIST)))
    }

    fun testHierarchyJavaLibraryClass() {
        myFixture.configureByFiles("Main.kt", "Boo.kt", "Doo.kt", "Foo.kt")
        rebuildProject()
        TestCase.assertEquals(setOf("Main.kt", "Foo.kt"), getReferentFiles(myFixture.findClass("java.util.AbstractList")))
        myFixture.addFileToProject("Another.kt", "")
        TestCase.assertEquals(setOf("Main.kt", "Foo.kt"), getReferentFiles(myFixture.findClass("java.util.AbstractList")))
    }

    fun testPrimaryConstructor() {
        myFixture.configureByFiles("Foo.kt", "Main.kt", "Doo.kt", "JavaClass.java", "WithoutUsages.kt")
        rebuildProject()
        // TODO: java class
        TestCase.assertEquals(setOf("Foo.kt", "Main.kt", "Doo.kt"), getReferentFilesForElementUnderCaret())
        addFileAndAssertIndexNotReady()
    }

    fun testSecondaryConstructor() {
        myFixture.configureByFiles("Foo.kt", "Main.kt", "Doo.kt", "JavaClass.java", "WithoutUsages.kt")
        rebuildProject()
        // TODO: java class
        TestCase.assertEquals(setOf("Foo.kt", "Main.kt", "Doo.kt"), getReferentFilesForElementUnderCaret())
        addFileAndAssertIndexNotReady()
    }

    fun testJavaConstructor() {
        myFixture.configureByFiles("JavaClass.java", "Foo.kt", "Main.kt", "Doo.kt", "WithoutUsages.kt")
        rebuildProject()
        TestCase.assertEquals(setOf("JavaClass.java", "Main.kt", "Doo.kt"), getReferentFilesForElementUnderCaret())
        addFileAndAssertIndexNotReady()
    }

    private fun assertIndexNotReady() = assertNull(getReferentFilesForElementUnderCaret())
    private fun assertUsageInMainFile() = assertEquals(setOf("Main.kt"), getReferentFilesForElementUnderCaret())
    private fun addFileAndAssertIndexNotReady(fileName: String = "Another.kt") {
        myFixture.addFileToProject(fileName, "")
        assertIndexNotReady()
    }
}
