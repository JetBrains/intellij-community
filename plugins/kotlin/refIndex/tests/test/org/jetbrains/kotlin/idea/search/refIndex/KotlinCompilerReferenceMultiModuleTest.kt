// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.SkipSlowTestLocally

@SkipSlowTestLocally
class KotlinCompilerReferenceMultiModuleTest : KotlinCompilerReferenceTestBase() {
    /**
     * [org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexService.buildScopeWithReferences]
     */
    fun `test dirty scope`() {
        // TODO support library declaration or rewrite it
        return
        val m1 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "m1", myFixture.tempDirFixture.findOrCreateDir("m1"))
        val f1 = myFixture.addFileToProject("m1/f1.java", "package one;\nclass JavaClass {}")
        val f2 = myFixture.addFileToProject("m1/f2.kt", "package one\nclass KotlinClass")
        val f3 = myFixture.addFileToProject("m1/f3.txt", "")
        val f8 = myFixture.addFileToProject("m1/f8.kt", "package one\nclass AnotherKotlinClass")

        PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "m2", myFixture.tempDirFixture.findOrCreateDir("m2"))
        val f4 = myFixture.addFileToProject("m2/f4.txt", "")

        PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "m3", myFixture.tempDirFixture.findOrCreateDir("m3"))
        val f5 = myFixture.addFileToProject("m3/f5.java", "package three;\nclass JavaClass {}")
        val f6 = myFixture.addFileToProject("m3/f6.kt", "package three\nclass KotlinClass")
        val f7 = myFixture.addFileToProject("m3/f7.txt", "")

        val m4 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "m4", myFixture.tempDirFixture.findOrCreateDir("m4"))
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
