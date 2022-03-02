// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.kotlin

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.patterns.uast.injectionHostUExpression
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceContributorEP
import com.intellij.psi.util.PropertyUtil
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.toUElementOfType
import org.junit.Test
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import kotlin.test.fail

@RunWith(JUnit38ClassRunner::class)
class KotlinUastReferencesTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE

    @Test
    fun `test original getter is visible when reference is under renaming`() {
        val myDisposable = Disposer.newDisposable("MockPsiReferenceContributor")
        try {
            registerReferenceContributor(myDisposable, MockPsiReferenceContributor::class.java)

            myFixture.configureByText(
                "KotlinBean.kt", """
                data class KotlinBean(val myF<caret>ield: String)

                val reference = "myField"

                """.trimIndent()
            )

            myFixture.renameElementAtCaret("myRenamedField")

            myFixture.checkResult(
                """
                data class KotlinBean(val myRenamedField: String)

                val reference = "myRenamedField"

                """.trimIndent()
            )
        } finally {
            Disposer.dispose(myDisposable)
        }
    }
}

private class GetterReference(
    val className: String,
    psiElement: PsiElement
) : PsiReferenceBase<PsiElement>(psiElement) {
    override fun resolve(): PsiMethod? {
        val psiClass = JavaPsiFacade.getInstance(element.project).findClass(className, element.resolveScope) ?: return null
        val name = element.toUElementOfType<UExpression>()?.evaluateString() ?: return null
        return PropertyUtil.getGetters(psiClass, name).firstOrNull()
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val resolve = resolve()
            ?: fail("can't resolve during rename, looks like someone renamed or removed the source element before updating references")

        val newName =
            if (PropertyUtil.getPropertyName(resolve) != null)
                PropertyUtil.getPropertyName(newElementName) ?: newElementName
            else newElementName

        return super.handleElementRename(newName)
    }

    override fun getVariants(): Array<Any> = emptyArray()
}

class MockPsiReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerUastReferenceProvider(
            injectionHostUExpression(),
            uastInjectionHostReferenceProvider { _, psiLanguageInjectionHost ->
                arrayOf(GetterReference("KotlinBean", psiLanguageInjectionHost))
            })
    }
}

fun registerReferenceContributor(disposable: Disposable, clazz: Class<out PsiReferenceContributor>) {
    PsiReferenceContributor.EP_NAME.point.registerExtension(
        PsiReferenceContributorEP().apply {
            implementationClass = clazz.name
            pluginDescriptor = DefaultPluginDescriptor("kotlin-uast-test")
        },
        disposable,
    )
}
