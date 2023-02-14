// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.common.kotlin

import com.intellij.psi.*
import junit.framework.TestCase
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.utils.sure
import org.jetbrains.uast.*
import org.jetbrains.uast.test.env.findElementByTextFromPsi
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.junit.Assert
import java.lang.IllegalStateException

interface UastResolveApiTestBase : UastPluginSelection {

    fun checkCallbackForDoWhile(filePath: String, uFile: UFile) {
        val facade = uFile.findFacade()
            ?: throw IllegalStateException("No facade found at ${uFile.asRefNames()}")
        val test = facade.methods.find { it.name == "test" }
            ?: throw IllegalStateException("Target function not found at ${uFile.asRefNames()}")
        val resolvedBinaryOperators: MutableList<PsiMethod> = mutableListOf()
        test.accept(object : AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                node.resolveOperator()?.let { resolvedBinaryOperators.add(it) }
                return super.visitBinaryExpression(node)
            }
        })
        Assert.assertEquals("Expect != (String.equals)", 1, resolvedBinaryOperators.size)
        val op = resolvedBinaryOperators.single()
        Assert.assertEquals("equals", op.name)

        val kt44412 = facade.methods.find { it.name == "kt44412" }
            ?: throw IllegalStateException("Target function not found at ${uFile.asRefNames()}")
        resolvedBinaryOperators.clear()
        val resolvedUnaryOperators: MutableList<PsiMethod> = mutableListOf()
        kt44412.accept(object : AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                node.resolveOperator()?.let { resolvedBinaryOperators.add(it) }
                return super.visitBinaryExpression(node)
            }

            override fun visitPrefixExpression(node: UPrefixExpression): Boolean {
                node.resolveOperator()?.let { resolvedUnaryOperators.add(it) }
                return super.visitPrefixExpression(node)
            }
        })
        Assert.assertEquals("Kotlin built-in >= (int.compareTo) and == (int.equals) are invisible", 0, resolvedBinaryOperators.size)
        Assert.assertEquals("Kotlin built-in ++ (int.inc) is invisible", 0, resolvedUnaryOperators.size)
    }

    fun checkCallbackForIf(filePath: String, uFile: UFile) {
        val facade = uFile.findFacade()
            ?: throw IllegalStateException("No facade found at ${uFile.asRefNames()}")
        val test = facade.methods.find { it.name == "test" }
            ?: throw IllegalStateException("Target function not found at ${uFile.asRefNames()}")
        val resolvedOperators: MutableList<PsiMethod> = mutableListOf()
        test.accept(object : AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                node.resolveOperator()?.let { resolvedOperators.add(it) }
                return super.visitBinaryExpression(node)
            }
        })
        Assert.assertEquals("Kotlin built-in * (int.times) and + (int.plus) are invisible", 0, resolvedOperators.size)
    }

    fun checkCallbackForMethodReference(filePath: String, uFile: UFile) {
        val facade = uFile.findFacade()
            ?: throw IllegalStateException("No facade found at ${uFile.asRefNames()}")
        // val x = Foo::bar
        val x = facade.fields.single()
        var barReference: PsiElement? = null
        x.accept(object : AbstractUastVisitor() {
            override fun visitCallableReferenceExpression(node: UCallableReferenceExpression): Boolean {
                barReference = node.resolve()
                return super.visitCallableReferenceExpression(node)
            }
        })
        Assert.assertNotNull("Foo::bar is not resolved", barReference)
        val barReferenceOrigin = (barReference as KtLightMethod).kotlinOrigin
        Assert.assertTrue("Foo::bar is not a function", barReferenceOrigin is KtNamedFunction)
        Assert.assertEquals("Foo.bar", (barReferenceOrigin as KtNamedFunction).fqName?.asString())
    }

    fun checkCallbackForImports(filePath: String, uFile: UFile) {
        uFile.imports.forEach { uImport ->
            if ((uImport.sourcePsi as? KtImportDirective)?.text?.endsWith("sleep") == true) {
                // There are two static [sleep] in [java.lang.Thread], so the import (w/o knowing its usage) can't be resolved to
                // a single function, hence `null` (as [resolve] result).
                // TODO: make [UImportStatement] a subtype of [UMultiResolvable], instead of [UResolvable]?
                return@forEach
            }
            val resolvedImport = uImport.resolve()
                ?: throw IllegalStateException("Unresolved import: ${uImport.asRenderString()}")
            val expected = when (resolvedImport) {
                is PsiClass -> {
                    // import java.lang.Thread.*
                    resolvedImport.name == "Thread" || resolvedImport.name == "UncaughtExceptionHandler"
                }
                is PsiMethod -> {
                    // import java.lang.Thread.currentThread
                    resolvedImport.name == "currentThread" ||
                            // import kotlin.collections.emptyList
                            (!isFirUastPlugin && resolvedImport.name == "emptyList")
                }
                is PsiField -> {
                    // import java.lang.Thread.NORM_PRIORITY
                    resolvedImport.name == "NORM_PRIORITY" ||
                            // import kotlin.Int.Companion.SIZE_BYTES
                            (!isFirUastPlugin && resolvedImport.name == "SIZE_BYTES")
                }
                is KtNamedFunction -> {
                    // import kotlin.collections.emptyList
                    isFirUastPlugin && resolvedImport.isTopLevel && resolvedImport.name == "emptyList"
                }
                is KtProperty -> {
                    // import kotlin.Int.Companion.SIZE_BYTES
                    isFirUastPlugin && resolvedImport.name == "SIZE_BYTES"
                }
                else -> false
            }
            Assert.assertTrue("Unexpected import: $resolvedImport", expected)
        }
    }

    fun checkCallbackForReceiverFun(filePath: String, uFile: UFile) {
        val facade = uFile.findFacade()
            ?: throw IllegalStateException("No facade found at ${uFile.asRefNames()}")
        // ... String.foo() = this.length
        val foo = facade.methods.find { it.name == "foo" }
            ?: throw IllegalStateException("Target function not found at ${uFile.asRefNames()}")
        var thisReference: PsiElement? = foo
        foo.accept(object : AbstractUastVisitor() {
            override fun visitThisExpression(node: UThisExpression): Boolean {
                thisReference = node.resolve()
                return super.visitThisExpression(node)
            }
        })
        Assert.assertNull("plain `this` has `null` label", thisReference)
    }

    fun checkCallbackForResolve(uFilePath: String, uFile: UFile) {
        fun UElement.assertResolveCall(callText: String, methodName: String = callText.substringBefore("(")) {
            this.findElementByTextFromPsi<UCallExpression>(callText).let {
                val resolve = it.resolve().sure { "resolving '$callText'" }
                TestCase.assertEquals(methodName, resolve.name)
            }
        }

        uFile.findElementByTextFromPsi<UElement>("bar").getParentOfType<UMethod>()!!.let { barMethod ->
            barMethod.assertResolveCall("foo()")
            barMethod.assertResolveCall("inlineFoo()")
            barMethod.assertResolveCall("forEach { println(it) }", "forEach")
            barMethod.assertResolveCall("joinToString()")
            barMethod.assertResolveCall("last()")
            barMethod.assertResolveCall("setValue(\"123\")")
            barMethod.assertResolveCall("contains(2 as Int)", "longRangeContains")
            barMethod.assertResolveCall("IntRange(1, 2)")
        }

        uFile.findElementByTextFromPsi<UElement>("barT").getParentOfType<UMethod>()!!.assertResolveCall("foo()")

        uFile.findElementByTextFromPsi<UElement>("listT").getParentOfType<UMethod>()!!.let { barMethod ->
            barMethod.assertResolveCall("isEmpty()")
            barMethod.assertResolveCall("foo()")
        }
    }

    fun checkCallbackForRetention(uFilePath: String, uFile: UFile) {

        fun checkRetentionAndResolve(uAnnotation: UAnnotation) {
            if (uAnnotation.qualifiedName?.endsWith("Retention") == true) {
                val value = uAnnotation.findAttributeValue("value")
                val reference = value as? UReferenceExpression
                TestCase.assertNotNull("Can't find the reference to @Retention value", reference)
                // Resolve @Retention value
                val resolvedValue = reference!!.resolve()
                TestCase.assertNotNull("Can't resolve @Retention value", resolvedValue)
                TestCase.assertEquals("SOURCE", (resolvedValue as? PsiNamedElement)?.name)
            }
        }

        // Lookup @Anno directly from the source file
        val anno = uFile.classes.find { it.name == "Anno" }
            ?: throw IllegalStateException("Target class not found at ${uFile.asRefNames()}")
        TestCase.assertTrue("@Anno is not an annotation?!", anno.isAnnotationType)
        anno.uAnnotations.forEach(::checkRetentionAndResolve)

        // Lookup @Anno indirectly from an annotated test class
        val testClass = uFile.classes.find { it.name == "TestClass" }
            ?: throw IllegalStateException("Target class not found at ${uFile.asRefNames()}")
        val annoOnTestClass = testClass.uAnnotations.find { it.qualifiedName?.endsWith("Anno") == true }
            ?: throw IllegalStateException("Target annotation not found at ${testClass.asSourceString()}")
        // Resolve @Anno to PsiClass
        val resolvedAnno = annoOnTestClass.resolve()
        TestCase.assertNotNull("Can't resolve @Anno on TestClass", resolvedAnno)
        for (psi in resolvedAnno!!.annotations) {
            val uAnnotation = anno.uAnnotations.find { it.javaPsi == psi } ?: continue
            val rebuiltAnnotation = psi.toUElement(UAnnotation::class.java)
            TestCase.assertNotNull("Should be able to rebuild UAnnotation from $psi", rebuiltAnnotation)
            TestCase.assertEquals(uAnnotation.qualifiedName, rebuiltAnnotation!!.qualifiedName)
            // Check Retention on a rebuilt UAnnotation
            checkRetentionAndResolve(rebuiltAnnotation)
        }
    }

    fun checkThreadSafe(uFilePath: String, uFile: UFile) {
        val safeClass = uFile.classes.find { it.name == "SafeClass" }
            ?: throw IllegalStateException("Target class not found at ${uFile.asRefNames()}")
        val k_delegate = safeClass.fields.find { it.name == "k\$delegate" }
            ?: throw IllegalStateException("Target field not found at ${safeClass.name}")
        // Without retrieving delegate expression type, it would be just "Lazy" (w/o type argument).
        TestCase.assertEquals("kotlin.Lazy<? extends SimpleSafeClass>", k_delegate.type.canonicalText)
    }
}
