// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * The bundle name in a constant of a copy of a Kotlin file, which the ModPlan rename edits, refers to the bundle: the copy resolves the
 * constant to the original file, see `PropertiesUastReferenceContributor.isField`.
 */
class K2BundleNameInCopyTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): KotlinLightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun testBundleNameInACopyRefersToTheBundle() = allowAnalysisOnEdt {
        myFixture.addFileToProject("FooBundle.properties", "foo.bar = test")
        myFixture.addFileToProject(
            "org/jetbrains/annotations/PropertyKey.kt",
            """
                package org.jetbrains.annotations

                @Retention(AnnotationRetention.BINARY)
                @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.FIELD)
                annotation class PropertyKey(val resourceBundle: String)
            """.trimIndent()
        )
        val file = myFixture.configureByText(
            "usages.kt",
            """
                import org.jetbrains.annotations.PropertyKey

                private val BUNDLE_NAME = "FooBundle"

                fun message(@PropertyKey(resourceBundle = BUNDLE_NAME) key: String) = key
            """.trimIndent()
        )

        assertEquals(listOf("FooBundle.properties"), bundlesOfBundleName(file))
        assertEquals(listOf("FooBundle.properties"), bundlesOfBundleName(file.copy() as PsiFile))
    }

    private fun bundlesOfBundleName(file: PsiFile): List<String> {
        val bundleName = PsiTreeUtil.findChildrenOfType(file, KtStringTemplateExpression::class.java).single { it.text == "\"FooBundle\"" }
        return bundleName.references.mapNotNull { (it.resolve() as? PsiFile)?.name }
    }
}
