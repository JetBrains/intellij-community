// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.navigation

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.navigation.KotlinClsCustomNavigationPolicy
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.invalidateLibraryCache
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile

class KotlinClsCustomNavigationPolicyTest : LightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        invalidateLibraryCache(project)
    }

    fun testCustomNavigationTarget() {
        val file = myFixture.configureByText(
            KotlinFileType.INSTANCE,
            """
                fun customTarget() {}
                fun usage() {
                    <caret>println()
                }
            """.trimIndent(),
        ) as KtFile
        val customTarget = file.declarations.single { it.name == "customTarget" }
        val compiledDeclaration = assertInstanceOf(
            myFixture.file.findReferenceAt(myFixture.caretOffset)?.resolve(),
            KtDeclaration::class.java,
        )
        assertTrue(compiledDeclaration.containingKtFile.isCompiled)

        val policy = object : KotlinClsCustomNavigationPolicy {
            override fun getNavigationElement(declaration: KtDeclaration) =
                customTarget.takeIf { declaration == compiledDeclaration }
        }
        val extensionPoint = ExtensionPointName<KotlinClsCustomNavigationPolicy>(
            "org.jetbrains.kotlin.clsCustomNavigationPolicy"
        ).point
        extensionPoint.registerExtension(policy, testRootDisposable)
        assertSame(customTarget, compiledDeclaration.navigationElement)
    }

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
}
