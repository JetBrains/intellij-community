// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.maven

import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibrarySourceModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider
import org.jetbrains.kotlin.idea.KotlinFileType
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
    @OptIn(KaExperimentalApi::class)
    fun testNavigateToCorrectStdlibSourcesInMavenProject() {
        MavenProjectsManager.getInstance(myFixture.project).initForTests()
        val stdlibCommonElement = configureAndResolve("""
            fun foo() {
                mapOf("" to "").<caret>mapNotNull {  }
            }
        """.trimIndent())!!
        assertEquals("Library sources of kotlin-stdlib", KaModuleProvider.getModule(project, stdlibCommonElement, null).moduleDescription)

        val stdlibJvmElement = configureAndResolve("""
            fun bar() {
                <caret>sortedMapOf(1 to 2)
            }
        """.trimIndent())!!
        assertEquals("Library sources of kotlin-stdlib", KaModuleProvider.getModule(project, stdlibJvmElement, null).moduleDescription)
    }

    private fun configureAndResolve(text: String): PsiElement? {
        myFixture.configureByText(KotlinFileType.INSTANCE, text)
        val ref = myFixture.file.findReferenceAt(myFixture.caretOffset)
        return ref?.resolve()?.navigationElement
    }
}
