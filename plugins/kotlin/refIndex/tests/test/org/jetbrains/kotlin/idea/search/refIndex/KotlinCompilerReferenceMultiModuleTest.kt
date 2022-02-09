// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiFile
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.SkipSlowTestLocally

@SkipSlowTestLocally
class KotlinCompilerReferenceMultiModuleTest : KotlinCompilerReferenceTestBase() {
    fun `test sub and super types`() {
        val m1 = createModule("m1")
        myFixture.addFileToProject("m1/k.kt", "package one\nopen class K")
        myFixture.addFileToProject("m1/kk.kt", "package one\nopen class KK : K()")
        myFixture.addFileToProject("m1/kkk.kt", "package one\nopen class KKK : KK()")

        val m2 = createModule("m2")
        myFixture.addFileToProject("m2/i.kt", "package two\ninterface I")
        myFixture.addFileToProject("m2/ii.kt", "package two\ninterface II : I")
        myFixture.addFileToProject("m2/iii.kt", "package two\ninterface III : II")
        ModuleRootModificationUtil.addDependency(m2, m1)

        val m3 = createModule("m3")
        myFixture.addFileToProject("m3/i2.kt", "package three\ninterface I2")
        myFixture.addFileToProject("m3/ii2.kt", "package three\ninterface II2 : I2")

        val m4 = createModule("m4")
        ModuleRootModificationUtil.addDependency(m4, m1)
        ModuleRootModificationUtil.addDependency(m4, m2)
        ModuleRootModificationUtil.addDependency(m4, m3)
        myFixture.addFileToProject(
            "m4/k_k2_i.kt",
            """
                package four
                
                import one.K
                import two.I
                
                open class K_K2_I : K(), I
            """.trimIndent(),
        )

        myFixture.addFileToProject(
            "m4/kk2_i_k.kt",
            """
                package four
                
                import three.I2

                open class K_K_K2_I_I2 : K_K2_I(), I2
            """.trimIndent(),
        )

        myFixture.addFileToProject(
            "m4/end_kk_iii_ii2.kt",
            """
                package four
                
                import one.KK
                import two.III
                import three.II2
                
                class END : KK(), III, II2
            """.trimIndent(),
        )

        installCompiler()
        rebuildProject()

        assertEquals(listOf("four.K_K2_I", "one.KK"), findSubOrSuperTypes("one.K", deep = false, subtypes = true))
        assertEquals(
            listOf("four.END", "four.K_K2_I", "four.K_K_K2_I_I2", "one.KK", "one.KKK"),
            findSubOrSuperTypes("one.K", deep = true, subtypes = true),
        )

        //assertEquals(listOf("four.K_K2_I", "three.I2"), findSubOrSuperTypes("four.K_K_K2_I_I2", deep = false, subtypes = false))
        //assertEquals(
        //    listOf("four.K_K2_I", "one.K", "three.I2", "two.I"),
        //    findSubOrSuperTypes("four.K_K_K2_I_I2", deep = true, subtypes = false)
        //)

        forEachBoolean { deep ->
            //assertEquals(listOf("two.I"), findSubOrSuperTypes("two.II", deep, subtypes = false))
            assertEquals(emptyList<String>(), findSubOrSuperTypes("four.END", deep, subtypes = true))
        }

        //assertEquals(
        //    listOf("one.K", "one.KK", "three.I2", "three.II2", "two.I", "two.II", "two.III"),
        //    findSubOrSuperTypes("four.END", deep = true, subtypes = false),
        //)
    }

    /**
     * [org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexService.buildScopeWithReferences]
     */
    fun `test dirty scope`() {
        val m1 = createModule("m1")
        val f1 = myFixture.addFileToProject("m1/f1.java", "package one;\nclass JavaClass {}")
        val f2 = myFixture.addFileToProject("m1/f2.kt", "package one\nclass KotlinClass")
        val f3 = myFixture.addFileToProject("m1/f3.txt", "")
        val f8 = myFixture.addFileToProject("m1/f8.kt", "package one\nclass AnotherKotlinClass")

        createModule("m2")
        val f4 = myFixture.addFileToProject("m2/f4.txt", "")

        createModule("m3")
        val f5 = myFixture.addFileToProject("m3/f5.java", "package three;\nclass JavaClass {}")
        val f6 = myFixture.addFileToProject("m3/f6.kt", "package three\nclass KotlinClass")
        val f7 = myFixture.addFileToProject("m3/f7.txt", "")

        val m4 = createModule("m4")
        val f9 = myFixture.addFileToProject("m4/f9.kt", "package four\nclass KotlinClass")
        ModuleRootModificationUtil.addDependency(m4, m1)

        val files = listOf(f1, f2, f3, f8, f4, f5, f6, f7, f9)
        installCompiler()
        rebuildProject()

        fun fullCompiledProjectCheck() = files.assertFilesInScope { it.name.endsWith(".kt") }
        fullCompiledProjectCheck()
        myFixture.renameElement(f3, "ff3.txt")
        fullCompiledProjectCheck()

        myFixture.renameElement(f2, "ff2.kt")
        files.assertFilesInScope { it == f6 }
    }

    private fun createModule(moduleName: String): Module {
        val moduleDir = myFixture.tempDirFixture.findOrCreateDir(moduleName)
        val module = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), moduleName, moduleDir)
        IdeaTestUtil.setModuleLanguageLevel(module, LanguageLevel.JDK_11)
        return module
    }

    private fun List<PsiFile>.assertFilesInScope(isFileNotInScope: (PsiFile) -> Boolean) =
        partition(isFileNotInScope).let { (ktFiles, otherFiles) ->
            val searchScope = KotlinCompilerReferenceIndexService[project]
                .scopeWithCodeReferences(myFixture.findClass(CommonClassNames.JAVA_UTIL_ARRAY_LIST))
                ?: error("scope not found")

            for (ktFile in ktFiles) {
                assertFalse(ktFile.name, ktFile.virtualFile in searchScope)
            }

            for (anotherFile in otherFiles) {
                assertTrue(anotherFile.name, anotherFile.virtualFile in searchScope)
            }
        }
}
