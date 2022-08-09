// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.kotlin.org.jetbrains.uast.test.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.testFramework.assertEqualsToFile
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.kotlin.AbstractKotlinUastTest
import org.jetbrains.uast.test.kotlin.TEST_KOTLIN_MODEL_DIR
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.UastVisitor
import java.io.File

abstract class AbstractKotlinNonVisitorConversionsTest : AbstractKotlinUastTest() {

    private fun getTestFile(testName: String, ext: String) =
        File(File(TEST_KOTLIN_MODEL_DIR, testName).canonicalPath + '.' + ext)

    override fun check(testName: String, file: UFile) {
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

        assertEqualsToFile("MissedElements", getTestFile(testName, "missed.txt"), missedText.toString())
    }
}
