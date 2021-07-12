// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.psi.CommonClassNames
import com.intellij.testFramework.SkipSlowTestLocally
import junit.framework.AssertionFailedError
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

    fun testMemberFunction() {
        myFixture.configureByFile("Main.kt")
        rebuildProject()
        assertIndexUnavailable()
    }

    fun testTopLevelConstantWithJvmName() {
        myFixture.configureByFiles("Main.kt", "Bar.kt", "Foo.kt", "Doo.kt", "Empty.java", "JavaRead.java")
        // JvmName for constants isn't supported
        assertThrows(AssertionFailedError::class.java) { rebuildProject() }
    }

    fun testSimpleUsageInFullyCompiledProject() = doTest(
        filesWithUsages = listOf("Main.kt", "Foo.kt", "Boo.kt"),
        extraFiles = listOf("Doo.kt"),
    )

    fun testJavaNestedClass() = doTest(
        filesWithUsages = listOf("JavaClass.java", "TypeUsage.kt", "ConstructorUsage.kt"),
        extraFiles = listOf("WithoutUsages.kt"),
    )

    fun testJavaInnerClass() = doTest(
        filesWithUsages = listOf("JavaClass.java", "TypeUsage.kt", "ConstructorUsage.kt"),
        extraFiles = listOf("WithoutUsages.kt"),
    )

    fun testPrimaryConstructor() = doTest(
        filesWithUsages = listOf("Foo.kt", "JavaClass.java", "Main.kt", "Doo.kt"),
        extraFiles = listOf("Doo.kt", "WithoutUsages.kt", "JavaClass2.java"),
    )

    fun testNestedPrimaryConstructor() = doTest(
        filesWithUsages = listOf("JavaClass.java", "Main.kt", "Doo.kt"),
        extraFiles = listOf("JavaClass2.java", "WithoutUsages.kt"),
        customMainFile = "Foo.kt",
    )

    fun testSecondaryConstructor() = doTest(
        filesWithUsages = listOf("Foo.kt", "JavaClass2.java", "Main.kt", "Doo.kt"),
        extraFiles = listOf("JavaClass.java", "WithoutUsages.kt"),
    )

    fun testJavaConstructor() = doTest(
        filesWithUsages = listOf("JavaClass.java", "Main.kt", "Doo.kt"),
        extraFiles = listOf("Foo.kt", "WithoutUsages.kt"),
    )

    fun testTopLevelFunction() = doTest(
        filesWithUsages = listOf("Main.kt", "Doo.kt", "Foo.kt", "JavaClass.java"),
        extraFiles = listOf("Bar.kt", "JavaClass2.java"),
    )

    fun testTopLevelFunctionWithJvmName() = doTest(
        filesWithUsages = listOf("Main.kt", "Doo.kt", "Foo.kt", "JavaClass.java"),
        extraFiles = listOf("Bar.kt", "JavaClass2.java"),
    )

    fun testTopLevelExtension() = doTest(
        filesWithUsages = listOf("Main.kt", "Bar.kt", "Foo.kt", "JavaClass.java"),
        extraFiles = listOf("Doo.kt", "JavaClass2.java"),
    )

    fun testTopLevelExtensionWithCustomFileName() = doTest(
        filesWithUsages = listOf("Main.kt", "Bar.kt", "Foo.kt", "JavaClass.java"),
        extraFiles = listOf("Doo.kt", "JavaClass2.java"),
    )

    fun testTopLevelProperty() = doTest(
        filesWithUsages = listOf("Main.kt", "Doo.kt", "Foo.kt", "JavaRead.java"),
        extraFiles = listOf("Empty.java", "Bar.kt"),
    )

    fun testTopLevelConstant() = doTest(
        filesWithUsages = listOf("Main.kt", "Doo.kt", "Foo.kt", "JavaRead.java"),
        extraFiles = listOf("Empty.java", "Bar.kt"),
    )

    fun testTopLevelConstantWithCustomFileName() = doTest(
        filesWithUsages = listOf("Main.kt", "Doo.kt", "Foo.kt", "JavaRead.java"),
        extraFiles = listOf("Empty.java", "Bar.kt"),
    )

    fun testTopLevelConstantJava() = doTest(
        filesWithUsages = listOf("JavaRead.java", "Doo.kt", "Foo.kt", "Main.kt"),
        extraFiles = listOf("Bar.kt", "Empty.java"),
    )

    fun testTopLevelConstantJavaWithCustomFileName() = doTest(
        filesWithUsages = listOf("JavaRead.java", "Doo.kt", "Foo.kt", "Main.kt"),
        extraFiles = listOf("Bar.kt", "Empty.java"),
    )

    fun testTopLevelExtensionProperty() = doTest(
        filesWithUsages = listOf("Main.kt", "Bar.kt", "Foo.kt", "JavaRead.java"),
        extraFiles = listOf("Doo.kt", "Empty.java"),
    )

    fun testTopLevelExtensionVariable() = doTest(
        filesWithUsages = listOf("Main.kt", "Bar.kt", "Foo.kt", "JavaRead.java", "JavaWrite.java", "Write.kt"),
        extraFiles = listOf("Doo.kt", "Empty.java"),
    )

    fun testTopLevelExtensionVariableWithJvmNameOnProperty() = doTest(
        filesWithUsages = listOf("Main.kt", "Bar.kt", "Foo.kt", "JavaRead.java", "JavaWrite.java", "Write.kt"),
        extraFiles = listOf("Doo.kt", "Empty.java"),
    )

    fun testTopLevelVariable() = doTest(
        filesWithUsages = listOf("Main.kt", "Read.kt", "Write.kt", "JavaRead.java", "JavaWrite.java"),
        extraFiles = listOf("Nothing.kt", "Empty.java"),
    )

    fun testTopLevelVariableWithJvmNameOnProperty() = doTest(
        filesWithUsages = listOf("Main.kt", "Read.kt", "Write.kt", "JavaRead.java", "JavaWrite.java"),
        extraFiles = listOf("Nothing.kt", "Empty.java"),
    )

    fun testTopLevelVariableWithCustomFileName() = doTest(
        filesWithUsages = listOf("Main.kt", "Read.kt", "Write.kt", "JavaRead.java", "JavaWrite.java"),
        extraFiles = listOf("Nothing.kt", "Empty.java"),
    )

    fun testTopLevelIsVariableWithCustomFileName() = doTest(
        filesWithUsages = listOf("Main.kt", "Read.kt", "Write.kt", "JavaRead.java", "JavaWrite.java"),
        extraFiles = listOf("Nothing.kt", "Empty.java"),
    )

    fun testTopLevelPropertyWithBackingField() = doTest(
        filesWithUsages = listOf("Main.kt", "Read.kt", "Write.kt", "JavaRead.java", "JavaWrite.java"),
        extraFiles = listOf("Nothing.kt", "Empty.java"),
    )

    fun testTopLevelPropertyWithCustomGetterAndSetter() = doTest(
        filesWithUsages = listOf("Main.kt", "Read.kt", "Write.kt", "JavaRead.java", "JavaWrite.java"),
        extraFiles = listOf("Nothing.kt", "Empty.java"),
    )

    fun testTopLevelVariableWithCustomGetterAndSetterAndMixedJvmName() = doTest(
        filesWithUsages = listOf("Main.kt", "Read.kt", "Write.kt", "JavaRead.java", "JavaWrite.java"),
        extraFiles = listOf("Nothing.kt", "Empty.java"),
    )

    fun testTopLevelVariableWithCustomGetterAndSetterAndJvmName() = doTest(
        filesWithUsages = listOf("Main.kt", "Read.kt", "Write.kt", "JavaRead.java", "JavaWrite.java"),
        extraFiles = listOf("Nothing.kt", "Empty.java"),
    )

    fun testTopLevelFunctionWithJvmOverloads() = doTest(
        filesWithUsages = listOf(
            "utilFile.kt",
            "AllArguments.java",
            "AllArguments.kt",
            "WithoutAll.java",
            "WithoutAll.kt",
            "WithoutLast.java",
            "WithoutLast.kt",
            "WithoutSecond.java",
            "WithoutSecond.kt",
        ),
        extraFiles = listOf(
            "Empty.java",
            "Empty.kt",
        ),
    )

    fun testTopLevelExtensionWithJvmOverloadsAndJvmName() = doTest(
        filesWithUsages = listOf(
            "utilFile.kt",
            "AllArguments.java",
            "AllArguments.kt",
            "WithoutAll.java",
            "WithoutAll.kt",
            "WithoutLast.java",
            "WithoutLast.kt",
            "WithoutSecond.java",
            "WithoutSecond.kt",
        ),
        extraFiles = listOf(
            "Empty.java",
            "Empty.kt",
        ),
    )

    fun testInnerClass() = doTest(
        filesWithUsages = listOf("Usage.kt", "JavaUsage.java"),
        extraFiles = listOf("Empty.kt", "Empty.java"),
        customMainFile = "MainClass.kt",
    )

    fun testInnerClassWithPackage() = doTest(
        filesWithUsages = listOf("Usage.kt", "JavaUsage.java"),
        extraFiles = listOf("Empty.kt", "Empty.java"),
        customMainFile = "MainClass.kt",
    )

    fun testNestedClass() = doTest(
        filesWithUsages = listOf("Usage.kt", "JavaUsage.java"),
        extraFiles = listOf("Empty.kt", "Empty.java"),
        customMainFile = "MainClass.kt",
    )

    fun testObject() = doTest(
        filesWithUsages = listOf("MyObject.kt", "Usage.kt", "JavaUsage.java"),
        extraFiles = listOf("Empty.kt", "Empty.java"),
    )

    fun testNestedObject() = doTest(
        filesWithUsages = listOf("Usage.kt", "JavaUsage.java"),
        extraFiles = listOf("Empty.kt", "Empty.java"),
        customMainFile = "MainClass.kt",
    )

    private fun doTest(filesWithUsages: List<String>, extraFiles: List<String>, customMainFile: String? = null) {
        val allFiles = listOfNotNull(customMainFile).plus(filesWithUsages).plus(extraFiles).distinct()
        val filesOutOfConfiguration = Path(testDataPath, name).listDirectoryEntries().filter { it.name !in allFiles }
        TestCase.assertTrue(
            "Files out of configuration: ${filesOutOfConfiguration.map { it.name }}",
            filesOutOfConfiguration.isEmpty(),
        )

        myFixture.configureByFiles(*allFiles.toTypedArray())
        rebuildProject()
        TestCase.assertEquals(filesWithUsages.toSet(), getReferentFilesForElementUnderCaret())
        addFileAndAssertIndexNotReady()
    }

    private fun assertIndexUnavailable() = assertNull(getReferentFilesForElementUnderCaret())
    private fun assertUsageInMainFile() = assertEquals(setOf("Main.kt"), getReferentFilesForElementUnderCaret())
    private fun addFileAndAssertIndexNotReady(fileName: String = "Another.kt") {
        myFixture.addFileToProject(fileName, "")
        assertIndexUnavailable()
    }
}
