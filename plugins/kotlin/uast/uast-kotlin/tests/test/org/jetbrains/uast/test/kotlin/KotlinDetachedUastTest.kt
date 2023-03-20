// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.kotlin

import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.JavaPsiFacadeEx
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import org.jetbrains.kotlin.analysis.decompiled.light.classes.KtLightClassForDecompiledDeclaration
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.findFunctionByName
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.uast.*
import org.jetbrains.uast.test.common.kotlin.orFail
import org.jetbrains.uast.test.env.findUElementByTextFromPsi
import org.jetbrains.uast.test.env.kotlin.assertEqualsToFile
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

@RunWith(JUnit38ClassRunner::class)
class KotlinDetachedUastTest : KotlinLightCodeInsightFixtureTestCase() {

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    fun testLiteralInAnnotation() {

        val psiFile = myFixture.configureByText("AnnotatedClass.kt", """
            class AnnotatedClass {
                    @JvmName(name = "")
                    fun bar(param: String) = null
            }
        """.trimIndent())

        fun psiElement(file: PsiFile): PsiElement = file.findElementAt(file.text.indexOf("\"\"")).orFail("literal")
                .getParentOfType<PsiLanguageInjectionHost>(false).orFail("host")
                .toUElement().orFail("uElement").getParentOfType<UClass>(false)
                .orFail("UClass").psi.orFail("psi")

        psiElement(psiFile).let {
            // Otherwise following asserts have no sense
            TestCase.assertTrue("psi element should be light ", it is KtLightElement<*, *>)
        }
        val copied = psiFile.copied()
        TestCase.assertNull("virtual file for copy should be null", copied.virtualFile)
        TestCase.assertNotNull("psi element in copy", psiElement(copied))
        TestCase.assertSame("copy.originalFile should be psiFile", copied.originalFile, psiFile)
        TestCase.assertSame("virtualFiles of element and file itself should be the same",
                            psiElement(copied).containingFile.originalFile.virtualFile,
                            copied.originalFile.virtualFile)
    }

    fun testDetachedResolve() {
        val psiFile = myFixture.configureByText(
            "AnnotatedClass.kt", """
            class AnnotatedClass {
                    @JvmName(name = "")
                    fun bar(param: String) { unknownFunc(param) }
            }
        """.trimIndent()
        ) as KtFile

        val detachedCall = psiFile.findDescendantOfType<KtCallExpression>()!!.copied()
        val uCallExpression = detachedCall.toUElementOfType<UCallExpression>()!!
        // at least it should not throw exceptions
        TestCase.assertNull(uCallExpression.methodName)
    }

    fun testCapturedTypeInExtensionReceiverOfCall() {
        val psiFile = myFixture.configureByText(
            "foo.kt", """
            class Foo<T>

            fun <K> K.extensionFunc() {}

            fun test(f: Foo<*>) {
                f.extensionFunc()
            }
        """.trimIndent()
        ) as KtFile

        val extensionFunctionCall = psiFile.findDescendantOfType<KtCallExpression>()!!
        val uCallExpression = extensionFunctionCall.toUElementOfType<UCallExpression>()!!

        TestCase.assertNotNull(uCallExpression.receiverType)
        TestCase.assertNotNull(uCallExpression.methodName)
    }

    fun testParameterInAnnotationClassFromFactory() {

        val detachedClass = KtPsiFactory(project).createClass("""
        annotation class MyAnnotation(val myParam: String = "default")
        """)

        detachedClass.findUElementByTextFromPsi<UElement>("default")
                .getParentOfType<UExpression>().let {
            TestCase.assertNotNull("it should return something at least", it)
        }

    }

    fun testLiteralInClassInitializerFromFactory() {

        val detachedClass = KtPsiFactory(project).createClass("""
        class MyAnnotation(){
            init {
                "default"
            }
        }
        """)

        val literalInside = detachedClass.findUElementByTextFromPsi<UElement>("default")
        generateSequence(literalInside, { it.uastParent }).count().let {
            TestCase.assertTrue("it should have some parents $it actually", it > 1)
        }

    }

    fun testAnonymousInnerClassWithIDELightClasses() {

        val detachedClass = myFixture.configureByText(
            "MyClass.kt", """
            class MyClass() {
              private val obj = object : MyClass() {}
            }
        """
        )

        val anonymousClass = detachedClass.findUElementByTextFromPsi<UObjectLiteralExpression>("object : MyClass() {}").declaration
        TestCase.assertEquals(
            "UClass (name = null), UObjectLiteralExpression, UField (name = obj), UClass (name = MyClass), UFile (package = )",
            generateSequence<UElement>(anonymousClass, { it.uastParent }).joinToString { it.asLogString() })

    }


    fun testDontConvertDetachedFunctions() {
        val ktFile = myFixture.configureByText(
            "MyClass.kt", """
            class MyClass() {
              fun foo1() = 42
            }
        """
        ) as KtFile
        val ktClass = ktFile.declarations.filterIsInstance<KtClass>().single()//.copied()
        val ktFunctionDetached = ktClass.findFunctionByName("foo1")!!
        runWriteAction { ktClass.delete() }
        TestCase.assertNull(ktFunctionDetached.toUElementOfType<UMethod>())
    }

    fun testRenameHandlers() {
        myFixture.configureByText(
            "JavaClass.java", """
            class JavaClass {
              void foo(){
                 new MyClass().getBar();
              }
            }
        """
        )

        myFixture.configureByText(
            "MyClass.kt", """
            class MyClass() {
              val b<caret>ar = 42
            }
        """
        )

        val element = myFixture.elementAtCaret

        val substitution =
            RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, editor).orFail("no element")
        val linkedMapOf = linkedMapOf<PsiElement, String>()
        RenameProcessor(project, substitution, "newName", false, false)
            .prepareRenaming(element, "newName", linkedMapOf)

        UsefulTestCase.assertTrue(linkedMapOf.any())
    }

    fun testConvertCompiledClass() {
        val rClass = JavaPsiFacadeEx.getInstanceEx(project).findClass("kotlin.text.Regex")
        assertInstanceOf(rClass, KtLightClassForDecompiledDeclaration::class.java)
        val uClass = rClass.toUElementOfType<UClass>()!!
        assertEqualsToFile(
            "compiled \"kotlin.text.Regex\" rendered",
            File(TEST_KOTLIN_MODEL_DIR, "Regex.compiled.log"),
            uClass.asRecursiveLogString()
        )
    }

}