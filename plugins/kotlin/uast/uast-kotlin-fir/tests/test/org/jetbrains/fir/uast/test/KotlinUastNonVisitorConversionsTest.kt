// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.fir.uast.test

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.testFramework.assertEqualsToFile
import org.jetbrains.fir.uast.test.env.kotlin.AbstractFirUastTest
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.kotlin.TEST_KOTLIN_MODEL_PATH
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.UastVisitor
import org.junit.Test
import java.nio.file.Path

class KotlinUastNonVisitorConversionsTest : AbstractFirUastTest() {

    override val testBasePath: Path = TEST_KOTLIN_MODEL_PATH

    override fun check(filePath: String, file: UFile) {
        val visitedElements = mutableSetOf<PsiElement>()
        file.accept(object : UastVisitor {
            override fun visitElement(node: UElement): Boolean {
                node.sourcePsi?.let {
                    visitedElements.add(it)
                }
                return false
            }
        })
        val missedText = StringBuilder()
        file.sourcePsi.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (!visitedElements.contains(element)) {
                    element.toUElement()?.let { uElement ->
                        missedText
                            .append(element.javaClass.canonicalName)
                            .append(": ")
                        generateSequence(uElement) { it.uastParent }.take(5).map { it.asLogString() }
                            .joinTo(missedText, " <- ")
                        missedText.appendLine()
                    }
                }
                super.visitElement(element)
            }
        })

        assertEqualsToFile("MissedElements", testBasePath.resolve("${getTestName(false)}.missed.txt").toFile(), missedText.toString())
    }

    @Test
    fun testClassAnnotation() = doCheck("ClassAnnotation.kt")

    @Test
    fun testLocalDeclarations() = doCheck("LocalDeclarations.kt")

    @Test
    fun testComments() = doCheck("Comments.kt")

    @Test
    fun testConstructors() = doCheck("Constructors.kt")

    @Test
    fun testSimpleAnnotated() = doCheck("SimpleAnnotated.kt")

    @Test
    fun testAnonymous() = doCheck("Anonymous.kt")

    @Test
    fun testAnnotationParameters() = doCheck("AnnotationParameters.kt")

    @Test
    fun testLambdas() = doCheck("Lambdas.kt")

    @Test
    fun testSuperCalls() = doCheck("SuperCalls.kt")

    @Test
    fun testPropertyInitializer() = doCheck("PropertyInitializer.kt")

    @Test
    fun testEnumValuesConstructors() = doCheck("EnumValuesConstructors.kt")

    @Test
    fun testNonTrivialIdentifiers() = doCheck("NonTrivialIdentifiers.kt")

    @Test
    fun testBrokenDataClass() = doCheck("BrokenDataClass.kt")

    @Test
    fun testBrokenGeneric() = doCheck("BrokenGeneric.kt")

    @Test
    fun testTryCatch() = doCheck("TryCatch.kt")
}