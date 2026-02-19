// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.junit

import com.intellij.execution.junit.JUnit6Framework
import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.util.ThreeState
import com.intellij.util.ThreeState.NO
import com.intellij.util.ThreeState.UNSURE
import com.intellij.util.ThreeState.YES
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework.Companion.asKtClassOrObject
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework.Companion.asKtNamedFunction
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinJUnit6Framework : JUnit6Framework(), KotlinPsiBasedTestFramework {
    private val psiBasedDelegate = KotlinJUnit5PsiDelegate(
        markerClassFqnsParam = markerClassFQNames,
        isUnderTestSources = this::isUnderTestSources,
        isKotlinTestClass = this::isTestClass
    )

    override fun responsibleFor(declaration: KtNamedDeclaration): Boolean =
        psiBasedDelegate.responsibleFor(declaration)

    override fun checkTestClass(declaration: KtClassOrObject): ThreeState = psiBasedDelegate.checkTestClass(declaration)

    override fun isTestClass(clazz: PsiElement): Boolean =
        when (val checkTestClass = checkTestClass(clazz)) {
            UNSURE -> super.isTestClass(clazz)
            else -> checkTestClass == YES
        }

    override fun isPotentialTestClass(clazz: PsiElement): Boolean =
        isTestClass(clazz) || psiBasedDelegate.isPotentialTestClass(clazz)

    override fun findSetUpMethod(clazz: PsiElement): PsiElement? =
        when (checkTestClass(clazz)) {
            UNSURE -> super.findSetUpMethod(clazz)
            NO -> null
            else -> clazz.asKtClassOrObject()?.let(psiBasedDelegate::findSetUp)
        }

    override fun findTearDownMethod(clazz: PsiElement): PsiElement? =
        when (checkTestClass(clazz)) {
            UNSURE -> super.findTearDownMethod(clazz)
            NO -> null
            else -> clazz.asKtClassOrObject()?.let(psiBasedDelegate::findTearDown)
        }

    override fun isIgnoredMethod(element: PsiElement?): Boolean =
        when (checkTestClass(element)) {
            UNSURE -> super.isIgnoredMethod(element)
            NO -> false
            else -> element.asKtNamedFunction()?.let(psiBasedDelegate::isIgnoredMethod) ?: false
        }

    override fun isTestMethod(element: PsiElement?): Boolean =
        when (checkTestClass(element)) {
            UNSURE -> super.isTestMethod(element)
            NO -> false
            else -> element.asKtNamedFunction()?.let(psiBasedDelegate::isTestMethod) ?: false
        }

    override fun getLanguage(): Language = KotlinLanguage.INSTANCE

    override fun isTestMethod(declaration: KtNamedFunction): Boolean =
        psiBasedDelegate.isTestMethod(declaration)

    override fun isIgnoredMethod(declaration: KtNamedFunction): Boolean =
        psiBasedDelegate.isIgnoredMethod(declaration)

    override fun getSetUpMethodFileTemplateDescriptor(): FileTemplateDescriptor? {
        return if (KotlinPluginModeProvider.isK1Mode()) {
            super.getSetUpMethodFileTemplateDescriptor()
        } else {
            FileTemplateDescriptor("Kotlin JUnit5 SetUp Function.kt")
        }
    }

    override fun getTearDownMethodFileTemplateDescriptor(): FileTemplateDescriptor? {
        return if (KotlinPluginModeProvider.isK1Mode()) {
            super.getTearDownMethodFileTemplateDescriptor()
        } else {
            FileTemplateDescriptor("Kotlin JUnit5 TearDown Function.kt")
        }
    }

    override fun getTestMethodFileTemplateDescriptor(): FileTemplateDescriptor {
        return if (KotlinPluginModeProvider.isK1Mode()) {
            super.getTestMethodFileTemplateDescriptor()
        } else {
            FileTemplateDescriptor("Kotlin JUnit5 Test Function.kt")
        }
    }

    override fun getTestClassFileTemplateDescriptor(): FileTemplateDescriptor? =
        if (KotlinPluginModeProvider.isK1Mode()) {
            super.getTestClassFileTemplateDescriptor()
        } else {
            FileTemplateDescriptor("Kotlin JUnit5 Test Class.kt")
        }
}