// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.CommonClassNames
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.SearchScope
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.SkipSlowTestLocally
import org.jetbrains.kotlin.search.assertWithExpectedScope

@SkipSlowTestLocally
class SearchScopeHumanReadableStringTest : KotlinCompilerReferenceTestBase() {
    override fun setUp() {
        super.setUp()
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

    fun `test array list with code usage scope`() {
        PsiSearchHelper.getInstance(project)
            .getCodeUsageScope(myFixture.findClass(CommonClassNames.JAVA_UTIL_ARRAY_LIST))
            .assertWithExpected(
                """
                    Intersection:
                      Union:
                        Project and Libraries
                        Scratches and Consoles
                      Intersection:
                        Union:
                          Files: [/one/JavaClass2.java, /one/JavaClass.java]
                          NOT:
                            Restricted by file types: [com.intellij.ide.highlighter.JavaFileType@6f940297, com.intellij.ide.highlighter.JavaClassFileType@51d6ced9] in
                              NOT:
                                EMPTY
                          Libraries
                        Union:
                          Files: [/two/KotlinClass.kt, /two/Main.kt]
                          NOT:
                            Restricted by file types: [org.jetbrains.kotlin.idea.KotlinFileType@e1715c6] in
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

    private fun SearchScope.assertWithExpected(expected: String): Unit = assertWithExpectedScope(this, expected)
}
