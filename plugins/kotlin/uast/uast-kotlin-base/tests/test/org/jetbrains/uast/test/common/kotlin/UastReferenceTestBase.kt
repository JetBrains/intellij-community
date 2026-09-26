// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.common.kotlin

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.patterns.uast.callExpression
import com.intellij.patterns.uast.injectionHostUExpression
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceContributorEP
import com.intellij.psi.registerUastReferenceProvider
import com.intellij.psi.uastInjectionHostReferenceProvider
import com.intellij.psi.util.PropertyUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import junit.framework.TestCase
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.toUElementOfType
import kotlin.test.fail

interface UastReferenceTestBase {
    fun `check original getter is visible when reference is under renaming`(myFixture: JavaCodeInsightTestFixture) {
        class GetterReference(
            val className: String,
            psiElement: PsiElement,
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

    fun checkConstructorCallPattern(myFixture: JavaCodeInsightTestFixture) {
        class ClassReference(
            val className: String,
            psiElement: PsiElement,
        ) : PsiReferenceBase<PsiElement>(psiElement) {
            override fun resolve(): PsiMethod? {
                val psiClass = JavaPsiFacade.getInstance(element.project).findClass(className, element.resolveScope) ?: return null
                return psiClass.constructors.singleOrNull()
            }

            override fun handleElementRename(newElementName: String): PsiElement {
                val resolved = resolve()
                TestCase.assertNotNull(resolved)

                return super.handleElementRename(newElementName)
            }
        }

        class ReferenceContributor : PsiReferenceContributor() {
            override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
                registrar.registerUastReferenceProvider(
                    injectionHostUExpression().inCall(callExpression().constructor("Test")),
                    uastInjectionHostReferenceProvider { uExpression, psiInjectionHost ->
                        TestCase.assertTrue(psiInjectionHost is KtStringTemplateExpression)
                        TestCase.assertTrue(uExpression is UPolyadicExpression)
                        TestCase.assertEquals("hello", uExpression.evaluateString())

                        val resolved = (uExpression.uastParent as? UCallExpression)?.resolve()
                        TestCase.assertNotNull(resolved)
                        TestCase.assertTrue(resolved!!.isConstructor)
                        TestCase.assertEquals("Test", resolved.name)

                        arrayOf(ClassReference(resolved.name, psiInjectionHost))
                    }
                )
            }
        }

        val myDisposable = Disposer.newDisposable("ReferenceContributor")
        try {
            registerReferenceContributor(myDisposable, ReferenceContributor::class.java)

            myFixture.configureByText(
                "Test.kt", """
                    sealed class Test(val greet: String)

                    class A : Test("h<caret>ello")
                """.trimIndent()
            )

            myFixture.renameElementAtCaret("Greeting")

            myFixture.checkResult(
                """
                    sealed class Greeting(val greet: String)

                    class A : Greeting("hello")
                """.trimIndent()
            )
        } finally {
            Disposer.dispose(myDisposable)
        }
    }

    private fun registerReferenceContributor(disposable: Disposable, clazz: Class<out PsiReferenceContributor>) {
        PsiReferenceContributor.EP_NAME.point.registerExtension(
            PsiReferenceContributorEP().apply {
                implementationClass = clazz.name
                pluginDescriptor = DefaultPluginDescriptor("kotlin-uast-test")
            },
            disposable,
        )
    }
}
