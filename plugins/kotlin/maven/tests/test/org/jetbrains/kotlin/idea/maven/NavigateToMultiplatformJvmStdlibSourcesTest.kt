// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.maven

import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.test.KotlinJdkAndSeparatedMultiplatformJvmStdlibDescriptor
import org.jetbrains.kotlin.idea.test.invalidateLibraryCache

class NavigateToMultipartJvmStdlibSourcesTest : LightJavaCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinJdkAndSeparatedMultiplatformJvmStdlibDescriptor.TWO_PART_STDLIB_WITH_SOURCES
    }

    override fun setUp() {
        super.setUp()
        invalidateLibraryCache(project)
    }

    // See KTIJ-23874
    @OptIn(K1ModeProjectStructureApi::class)
    fun testNavigateToCorrectStdlibSourcesInMavenProject() {
        MavenProjectsManager.getInstance(myFixture.project).initForTests()
        val stdlibCommonElement = configureAndResolve("""
            fun foo() {
                mapOf("" to "").<caret>mapNotNull {  }
            }
        """.trimIndent())
        assertEquals("<sources for library kotlin-stdlib>", stdlibCommonElement?.moduleInfo?.name.toString())

        val stdlibJvmElement = configureAndResolve("""
            fun bar() {
                <caret>sortedMapOf(1 to 2)
            }
        """.trimIndent())
        assertEquals("<sources for library kotlin-stdlib>", stdlibJvmElement?.moduleInfo?.name.toString())
    }

    private fun configureAndResolve(text: String): PsiElement? {
        myFixture.configureByText(KotlinFileType.INSTANCE, text)
        val ref = myFixture.file.findReferenceAt(myFixture.caretOffset)
        return ref?.resolve()?.navigationElement
    }
}
