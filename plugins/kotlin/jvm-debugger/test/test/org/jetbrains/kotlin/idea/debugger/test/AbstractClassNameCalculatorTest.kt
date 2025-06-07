// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import org.jetbrains.kotlin.idea.debugger.base.util.ClassNameCalculator
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

abstract class AbstractClassNameCalculatorTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    protected fun doTest(unused: String) {
        val testFile = dataFile()
        myFixture.configureByFile(testFile)

        project.executeWriteCommand("Add class name information") {
            deleteExistingComments(file)

            val ktFile = file as KtFile
            val allNames = ClassNameCalculator.getClassNames(ktFile)

            val ktPsiFactory = KtPsiFactory(project)

            for ((element, name) in allNames) {
                val comment = ktPsiFactory.createComment("/* $name */")
                element.addBefore(comment, element.firstChild)
            }
        }

        myFixture.checkResultByFile(testFile)
    }

    private fun deleteExistingComments(file: PsiFile) {
        val comments = mutableListOf<PsiComment>()

        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitComment(comment: PsiComment) {
                comments += comment
            }
        })

        comments.forEach { it.delete() }
    }
}
