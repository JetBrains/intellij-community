// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.navigation

import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlFile
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KtResolveExtensionProvider
import org.jetbrains.kotlin.idea.fir.extensions.KtResolveExtensionProviderForTests
import org.jetbrains.kotlin.idea.navigation.KotlinResolveExtensionGeneratedSourcesFilter
import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveTest

abstract class AbstractResolveExtensionGeneratedSourcesFilterTest : AbstractReferenceResolveTest() {
    override fun isFirPlugin(): Boolean = true

    private lateinit var xmlFile: XmlFile
    private val filter = KotlinResolveExtensionGeneratedSourcesFilter()

    override fun setUp() {
        super.setUp()
        @Language("xml")
        val xmlFile = myFixture.addFileToProject("data.xml", """
            <xml>
                <package>generated</package>
                <function name='generatedTopLevelFun'/>
                <class name='GeneratedClass'>
                    <function name='generatedMemberFun'/>
                </class>
            </xml>
        """.trimIndent()) as XmlFile
        this.xmlFile = xmlFile

        project.extensionArea.getExtensionPoint(KtResolveExtensionProvider.EP_NAME)
            .registerExtension(KtResolveExtensionProviderForTests(), testRootDisposable)
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun checkResolvedTo(element: PsiElement) {
        assertTrue(filter.isGeneratedSource(element.containingFile.virtualFile, project))
        // This method is usually called from a background thread, but we need to call it inline here
        // from the test's thread, which is registered as the EDT.
        val navTargets = allowAnalysisOnEdt { filter.getOriginalElements(element) }
        assertNotEmpty(navTargets)
        navTargets.forEach {
            assertSame(xmlFile, it.containingFile)
        }
    }
}
