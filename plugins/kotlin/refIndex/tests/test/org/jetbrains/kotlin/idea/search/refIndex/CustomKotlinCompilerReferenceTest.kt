// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.psi.CommonClassNames
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.testFramework.SkipSlowTestLocally
import junit.framework.AssertionFailedError
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.refactoring.fqName.getKotlinFqName
import org.jetbrains.kotlin.test.KotlinRoot
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.reflect.KFunction
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberFunctions

@SkipSlowTestLocally
class CustomKotlinCompilerReferenceTest : KotlinCompilerReferenceTestBase() {
    override fun getTestDataPath(): String = KotlinRoot.DIR
        .resolve("refIndex/tests/testData/")
        .resolve("customCompilerIndexData")
        .path + "/"

    override fun setUp() {
        super.setUp()
        installCompiler()
        myFixture.testDataPath = testDataPath + name
    }

    private fun assertIndexUnavailable() = assertNull(getReferentFilesForElementUnderCaret())
    private fun assertUsageInMainFile() = assertEquals(setOf("Main.kt"), getReferentFilesForElementUnderCaret())
    private fun addFileAndAssertIndexNotReady(fileName: String = "Another.kt") {
        myFixture.addFileToProject(fileName, "")
        assertIndexUnavailable()
    }

    fun `test match testData with tests`() {
        val testNames = this::class.declaredMemberFunctions.filter { it.visibility == KVisibility.PUBLIC }.map(KFunction<*>::name).toSet()
        for (testDirectory in Path(testDataPath).listDirectoryEntries()) {
            if (!testDirectory.isDirectory() || testDirectory.listDirectoryEntries().isEmpty()) continue

            val testDirectoryName = testDirectory.name
            assertTrue("Test not found for '$testDirectoryName' directory", testDirectoryName in testNames)
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
        TestCase.assertEquals(null, getReferentFiles(myFixture.findClass(CommonClassNames.JAVA_UTIL_ARRAY_LIST), false))
    }

    fun testHierarchyJavaLibraryClass() {
        myFixture.configureByFiles("Main.kt", "Boo.kt", "Doo.kt", "Foo.kt")
        rebuildProject()
        TestCase.assertEquals(null, getReferentFiles(myFixture.findClass("java.util.AbstractList"), false))
        myFixture.addFileToProject("Another.kt", "")
        TestCase.assertEquals(null, getReferentFiles(myFixture.findClass("java.util.AbstractList"), false))
    }

    fun testTopLevelConstantWithJvmName() {
        myFixture.configureByFiles("Main.kt", "Bar.kt", "Foo.kt", "Doo.kt", "Empty.java", "JavaRead.java")
        // JvmName for constants isn't supported
        assertThrows(AssertionFailedError::class.java) { rebuildProject() }
    }

    fun `test sub and super types`() {
        myFixture.addFileToProject(
            "one/two/Main.kt",
            """
                package one.two
                open class K
                open class KK : K()
                open class KK2 : K()
                open class KKK : KK()
                open class KKK2 : KK()
            """.trimIndent()
        )

        val clazz = myFixture.findClass("one.two.K")
        val deepSubtypes = ClassInheritorsSearch.search(clazz, true)
            .findAll()
            .map { it.getKotlinFqName().toString() }
            .sorted()

        val subtypes = ClassInheritorsSearch.search(clazz, false)
            .findAll()
            .map { it.getKotlinFqName().toString() }
            .sorted()

        assertEquals(listOf("one.two.KK", "one.two.KK2"), subtypes)
        assertEquals(listOf("one.two.KK", "one.two.KK2", "one.two.KKK", "one.two.KKK2"), deepSubtypes)

        rebuildProject()
        forEachBoolean { deep ->
            assertEquals(emptyList<String>(), findSubOrSuperTypes("one.two.K", deep, subtypes = false))
            assertEquals(emptyList<String>(), findSubOrSuperTypes("Another", deep, subtypes = false))

            assertEquals(emptyList<String>(), findSubOrSuperTypes("one.two.KKK", deep, subtypes = true))
            assertEquals(emptyList<String>(), findSubOrSuperTypes("Another", deep, subtypes = true))
        }

        assertEquals(subtypes, findSubOrSuperTypes("one.two.K", deep = false, subtypes = true))
        assertEquals(deepSubtypes, findSubOrSuperTypes("one.two.K", deep = true, subtypes = true))

        assertEquals(listOf("one.two.KK"), findSubOrSuperTypes("one.two.KKK", deep = false, subtypes = false))
        assertEquals(listOf("one.two.K", "one.two.KK"), findSubOrSuperTypes("one.two.KKK", deep = true, subtypes = false))
    }
}
