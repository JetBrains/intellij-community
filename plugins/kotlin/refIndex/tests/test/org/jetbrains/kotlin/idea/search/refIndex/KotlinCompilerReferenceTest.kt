// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.psi.CommonClassNames
import com.intellij.testFramework.SkipSlowTestLocally
import junit.framework.TestCase
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.reflect.KFunction
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberFunctions

@SkipSlowTestLocally
class KotlinCompilerReferenceTest : KotlinCompilerReferenceTestBase() {
    override fun getTestDataPath(): String = getTestDataPath("compilerIndex")

    override fun setUp() {
        super.setUp()
        installCompiler()
        myFixture.testDataPath = testDataPath + name
    }

    fun `test match testData with tests`() {
        val testNames = this::class.declaredMemberFunctions.filter { it.visibility == KVisibility.PUBLIC }.map(KFunction<*>::name).toSet()
        for (testDirectory in Path(testDataPath).listDirectoryEntries()) {
            val testName = testDirectory.name
            assertTrue("Test not found for '$testName' directory", testName in testNames)
        }
    }

    fun testIsNotReady() {
        myFixture.configureByFile("Main.kt")
        assertIndexUnavailable()
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
        myFixture.configureByFiles("Foo.kt", "Main.kt", "Doo.kt", "JavaClass.java", "JavaClass2.java", "WithoutUsages.kt")
        rebuildProject()
        TestCase.assertEquals(setOf("JavaClass.java", "Foo.kt", "Main.kt", "Doo.kt"), getReferentFilesForElementUnderCaret())
        addFileAndAssertIndexNotReady()
    }

    fun testSecondaryConstructor() {
        myFixture.configureByFiles("Foo.kt", "Main.kt", "Doo.kt", "JavaClass.java", "JavaClass2.java", "WithoutUsages.kt")
        rebuildProject()
        TestCase.assertEquals(setOf("JavaClass2.java", "Foo.kt", "Main.kt", "Doo.kt"), getReferentFilesForElementUnderCaret())
        addFileAndAssertIndexNotReady()
    }

    fun testJavaConstructor() {
        myFixture.configureByFiles("JavaClass.java", "Foo.kt", "Main.kt", "Doo.kt", "WithoutUsages.kt")
        rebuildProject()
        TestCase.assertEquals(setOf("JavaClass.java", "Main.kt", "Doo.kt"), getReferentFilesForElementUnderCaret())
        addFileAndAssertIndexNotReady()
    }

    fun testMemberFunction() {
        myFixture.configureByFile("Main.kt")
        rebuildProject()
        assertIndexUnavailable()
    }

    fun testTopLevelFunction() {
        myFixture.configureByFiles("Main.kt", "Bar.kt", "Foo.kt", "Doo.kt", "JavaClass.java", "JavaClass2.java")
        rebuildProject()
        TestCase.assertEquals(setOf("Doo.kt", "Foo.kt", "Main.kt", "JavaClass.java"), getReferentFilesForElementUnderCaret())
        addFileAndAssertIndexNotReady()
    }

    fun testTopLevelFunctionWithJvmName() {
        myFixture.configureByFiles("Main.kt", "Bar.kt", "Foo.kt", "Doo.kt", "JavaClass.java", "JavaClass2.java")
        rebuildProject()
        TestCase.assertEquals(setOf("Doo.kt", "Foo.kt", "Main.kt", "JavaClass.java"), getReferentFilesForElementUnderCaret())
        addFileAndAssertIndexNotReady()
    }

    fun testTopLevelExtension() {
        myFixture.configureByFiles("Main.kt", "Bar.kt", "Foo.kt", "Doo.kt", "JavaClass.java", "JavaClass2.java")
        rebuildProject()
        TestCase.assertEquals(setOf("Main.kt", "Bar.kt", "Foo.kt", "JavaClass.java"), getReferentFilesForElementUnderCaret())
        addFileAndAssertIndexNotReady()
    }

    fun testTopLevelExtensionWithCustomFileName() {
        myFixture.configureByFiles("Main.kt", "Bar.kt", "Foo.kt", "Doo.kt", "JavaClass.java", "JavaClass2.java")
        rebuildProject()
        TestCase.assertEquals(setOf("Main.kt", "Bar.kt", "Foo.kt", "JavaClass.java"), getReferentFilesForElementUnderCaret())
        addFileAndAssertIndexNotReady()
    }

    fun testTopLevelProperty() {
        myFixture.configureByFiles("Main.kt", "Bar.kt", "Foo.kt", "Doo.kt", "Empty.java", "JavaRead.java")
        rebuildProject()
        TestCase.assertEquals(setOf("Doo.kt", "Foo.kt", "Main.kt", "JavaRead.java"), getReferentFilesForElementUnderCaret())
        addFileAndAssertIndexNotReady()
    }

    fun testTopLevelConstant() {
        myFixture.configureByFiles("Main.kt", "Bar.kt", "Foo.kt", "Doo.kt", "Empty.java", "JavaRead.java")
        rebuildProject()
        TestCase.assertEquals(setOf("Doo.kt", "Foo.kt", "Main.kt", "JavaRead.java"), getReferentFilesForElementUnderCaret())
        addFileAndAssertIndexNotReady()
    }

    fun testTopLevelConstantWithCustomFileName() {
        myFixture.configureByFiles("Main.kt", "Bar.kt", "Foo.kt", "Doo.kt", "Empty.java", "JavaRead.java")
        rebuildProject()
        TestCase.assertEquals(setOf("Doo.kt", "Foo.kt", "Main.kt", "JavaRead.java"), getReferentFilesForElementUnderCaret())
        addFileAndAssertIndexNotReady()
    }

    fun testTopLevelConstantJava() {
        myFixture.configureByFiles("JavaRead.java", "Main.kt", "Bar.kt", "Foo.kt", "Doo.kt", "Empty.java")
        rebuildProject()
        TestCase.assertEquals(setOf("Doo.kt", "Foo.kt", "Main.kt", "JavaRead.java"), getReferentFilesForElementUnderCaret())
        addFileAndAssertIndexNotReady()
    }

    fun testTopLevelConstantJavaWithCustomFileName() {
        myFixture.configureByFiles("JavaRead.java", "Main.kt", "Bar.kt", "Foo.kt", "Doo.kt", "Empty.java")
        rebuildProject()
        TestCase.assertEquals(setOf("Doo.kt", "Foo.kt", "Main.kt", "JavaRead.java"), getReferentFilesForElementUnderCaret())
        addFileAndAssertIndexNotReady()
    }

    fun testTopLevelExtensionProperty() {
        myFixture.configureByFiles("Main.kt", "Bar.kt", "Foo.kt", "Doo.kt", "Empty.java", "JavaRead.java")
        rebuildProject()
        TestCase.assertEquals(setOf("Main.kt", "Bar.kt", "Foo.kt", "JavaRead.java"), getReferentFilesForElementUnderCaret())
        addFileAndAssertIndexNotReady()
    }

    fun testTopLevelExtensionVariable() {
        myFixture.configureByFiles("Main.kt", "Bar.kt", "Foo.kt", "Doo.kt", "Empty.java", "JavaRead.java", "JavaWrite.java", "Write.kt")
        rebuildProject()
        TestCase.assertEquals(
            setOf("Main.kt", "Bar.kt", "Foo.kt", "JavaRead.java", "JavaWrite.java", "Write.kt"),
            getReferentFilesForElementUnderCaret(),
        )

        addFileAndAssertIndexNotReady()
    }

    fun testTopLevelVariable() {
        myFixture.configureByFiles("Main.kt", "Nothing.kt", "Read.kt", "Write.kt", "JavaRead.java", "JavaWrite.java", "Empty.java")
        rebuildProject()
        TestCase.assertEquals(
            setOf("Main.kt", "Read.kt", "Write.kt", "JavaRead.java", "JavaWrite.java"),
            getReferentFilesForElementUnderCaret(),
        )

        addFileAndAssertIndexNotReady()
    }

    fun testTopLevelVariableWithCustomFileName() {
        myFixture.configureByFiles("Main.kt", "Nothing.kt", "Read.kt", "Write.kt", "JavaRead.java", "JavaWrite.java", "Empty.java")
        rebuildProject()
        TestCase.assertEquals(
            setOf("Main.kt", "Read.kt", "Write.kt", "JavaRead.java", "JavaWrite.java"),
            getReferentFilesForElementUnderCaret(),
        )

        addFileAndAssertIndexNotReady()
    }

    fun testTopLevelIsVariableWithCustomFileName() {
        myFixture.configureByFiles("Main.kt", "Nothing.kt", "Read.kt", "Write.kt", "JavaRead.java", "JavaWrite.java", "Empty.java")
        rebuildProject()
        TestCase.assertEquals(
            setOf("Main.kt", "Read.kt", "Write.kt", "JavaRead.java", "JavaWrite.java"),
            getReferentFilesForElementUnderCaret(),
        )

        addFileAndAssertIndexNotReady()
    }

    fun testTopLevelPropertyWithBackingField() {
        myFixture.configureByFiles("Main.kt", "Nothing.kt", "Read.kt", "Write.kt", "JavaRead.java", "JavaWrite.java", "Empty.java")
        rebuildProject()
        TestCase.assertEquals(
            setOf("Main.kt", "Read.kt", "Write.kt", "JavaRead.java", "JavaWrite.java"),
            getReferentFilesForElementUnderCaret(),
        )

        addFileAndAssertIndexNotReady()
    }

    fun testTopLevelPropertyWithCustomGetterAndSetter() {
        myFixture.configureByFiles("Main.kt", "Nothing.kt", "Read.kt", "Write.kt", "JavaRead.java", "JavaWrite.java", "Empty.java")
        rebuildProject()
        TestCase.assertEquals(
            setOf("Main.kt", "Read.kt", "Write.kt", "JavaRead.java", "JavaWrite.java"),
            getReferentFilesForElementUnderCaret(),
        )

        addFileAndAssertIndexNotReady()
    }

    private fun assertIndexUnavailable() = assertNull(getReferentFilesForElementUnderCaret())
    private fun assertUsageInMainFile() = assertEquals(setOf("Main.kt"), getReferentFilesForElementUnderCaret())
    private fun addFileAndAssertIndexNotReady(fileName: String = "Another.kt") {
        myFixture.addFileToProject(fileName, "")
        assertIndexUnavailable()
    }
}
