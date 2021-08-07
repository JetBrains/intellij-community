// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.compiler.CompilerReferenceService
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.CommonClassNames
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.SearchScope
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.runAll
import kotlin.properties.Delegates

@SkipSlowTestLocally
class SearchScopeHumanReadableStringTest : KotlinCompilerReferenceTestBase() {
    private var defaultCommonEnableState by Delegates.notNull<Boolean>()

    override fun setUp() {
        super.setUp()
        defaultCommonEnableState = CompilerReferenceService.IS_ENABLED_KEY.asBoolean()
        CompilerReferenceService.IS_ENABLED_KEY.setValue(true)

        myFixture.addFileToProject(
            "one/JavaClass.java",
            """
                package one;
                import java.util.ArrayList;
                public class JavaClass {
                  public static void main(String[] args){
                    new ArrayList<String>();
                  }
                }
            """.trimIndent(),
        )

        myFixture.addFileToProject(
            "one/JavaClass2.java",
            """
                package one;
                import java.util.ArrayList;
                public class JavaClass2 {
                  public static void main(String[] args){
                    new ArrayList<String>();
                  }
                }
            """.trimIndent(),
        )

        myFixture.addFileToProject(
            "two/KotlinClass.kt",
            """
               package two
               import java.util.ArrayList
               import one.JavaClass
               class KotlinClass {
                 fun test() {
                   ArrayList<String>()
                   JavaClass()
                 }
               }
            """.trimIndent()
        )

        myFixture.addFileToProject(
            "two/Main.kt",
            """
               package two
               import java.util.ArrayList
               fun main() {           
                 ArrayList<String>()
               }
            """.trimIndent()
        )

        val myModule = PsiTestUtil.addModule(
            project,
            JavaModuleType.getModuleType(),
            "myModule",
            myFixture.tempDirFixture.findOrCreateDir("myModule"),
        )

        ModuleRootModificationUtil.addDependency(myModule, module)
        myFixture.addFileToProject("myModule/JavaClass.java",
            """
                package three;
                import two.KotlinClass;
                public class JavaClass {
                  void test() {
                    new KotlinClass();
                  }
                }
            """.trimIndent()
        )

        installCompiler()
        rebuildProject()
    }

    override fun tearDown() = runAll(
        { CompilerReferenceService.IS_ENABLED_KEY.setValue(defaultCommonEnableState) },
        { super.tearDown() },
    )

    fun `test array list with code usage scope`() {
        PsiSearchHelper.getInstance(project)
            .getCodeUsageScope(myFixture.findClass(CommonClassNames.JAVA_UTIL_ARRAY_LIST))
            .assertWithExpected(
                """
                    Intersection:
                      Union:
                        Project and Libraries
                        Scratches and Consoles
                      Union:
                        Files: [/one/JavaClass.java, /one/JavaClass2.java]; search in libraries: false
                        NOT:
                          Restricted by file types: [com.intellij.ide.highlighter.JavaClassFileType@aec9c14, com.intellij.ide.highlighter.JavaFileType@e99bb56] in
                            NOT:
                              EMPTY
                        Libraries
                    
                """.trimIndent(),
            )
    }

    fun `test array list with use scope`() {
        PsiSearchHelper.getInstance(project).getUseScope(myFixture.findClass(CommonClassNames.JAVA_UTIL_ARRAY_LIST)).assertWithExpected(
            """
               Union:
                 Project and Libraries
                 Scratches and Consoles
               
            """.trimIndent(),
        )
    }

    fun `test java class with code usage scope`() {
        PsiSearchHelper.getInstance(project)
            .getCodeUsageScope(myFixture.findClass("one.JavaClass"))
            .assertWithExpected(
                """
                    Intersection:
                      Union:
                        Modules with dependents:
                          roots: [0]
                          including dependents: [0, myModule]
                        Scratches and Consoles
                      Intersection:
                        Union:
                          Files: [/one/JavaClass.java]
                          NOT:
                            Restricted by file types: [com.intellij.ide.highlighter.JavaClassFileType@4b8b8d4f, com.intellij.ide.highlighter.JavaFileType@4cecc653] in
                              NOT:
                                EMPTY
                        Union:
                          Files: [/two/KotlinClass.kt]
                          NOT:
                            Restricted by file types: [org.jetbrains.kotlin.idea.KotlinFileType@5cf4beea] in
                              NOT:
                                EMPTY
                    
                """.trimIndent(),
            )
    }

    fun `test java class with use scope`() {
        PsiSearchHelper.getInstance(project).getUseScope(myFixture.findClass("one.JavaClass")).assertWithExpected(
            """
               Union:
                 Modules with dependents:
                   roots: [3]
                   including dependents: [3, myModule]
                 Scratches and Consoles
               
            """.trimIndent(),
        )
    }

    private fun SearchScope.assertWithExpected(expected: String): Unit = with(KotlinCompilerReferenceIndexVerifierAction) {
        val actualText = this@assertWithExpected.toHumanReadableString()
        val expectedLines = expected.lines()
        val actualLines = actualText.lines()
        try {
            assertEquals(expectedLines.size, actualLines.size)
            for ((index, expectedLine) in expectedLines.withIndex()) {
                val actualLine = actualLines[index]
                val firstExpectedWord = stablePartOfLineRegex.find(expectedLine)
                    ?: error("stable part is not found in expected '$expectedLine'")

                val firstActualWord = stablePartOfLineRegex.find(actualLine) ?: error("stable part is not found in actual '$actualLine'")
                assertEquals(firstExpectedWord.value, firstActualWord.value)
            }
        } catch (e: AssertionError) {
            System.err.println(e.message)
            assertEquals(expected, actualText)
        }
    }

    companion object {
        private val stablePartOfLineRegex = Regex("[^\\[]*")
    }
}
