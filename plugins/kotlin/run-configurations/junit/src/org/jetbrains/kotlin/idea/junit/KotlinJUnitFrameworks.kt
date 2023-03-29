// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.junit

import com.intellij.execution.junit.JUnit3Framework
import com.intellij.execution.junit.JUnit4Framework
import com.intellij.execution.junit.JUnit5Framework
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.junit.framework.JUnit3KotlinTestFramework
import org.jetbrains.kotlin.idea.junit.framework.JUnit4KotlinTestFramework
import org.jetbrains.kotlin.idea.junit.framework.JUnit5KotlinTestFramework
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFramework
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction

interface KotlinTestFrameworkAdapter

class KotlinJUnit3Framework: JUnit3Framework(), KotlinTestFrameworkAdapter {
    private val kotlinDelegateClass = JUnit3KotlinTestFramework::class.java
    private val kotlinDelegate: KotlinTestFramework by lazy { KotlinTestFramework.EXTENSION_NAME.extensionList.first { it.javaClass == kotlinDelegateClass } }

    override fun isTestClass(clazz: PsiElement): Boolean {
        val ktClassOrObject = clazz.asKtClassOrObject() ?: return false
        return kotlinDelegate.isTestClass(ktClassOrObject)
    }

    override fun findSetUpMethod(clazz: PsiElement): PsiElement? {
        val ktClassOrObject = clazz.asKtClassOrObject() ?: return null
        return kotlinDelegate.findSetUp(ktClassOrObject)
    }

    override fun findTearDownMethod(clazz: PsiElement): PsiElement? {
        val ktClassOrObject = clazz.asKtClassOrObject() ?: return null
        return kotlinDelegate.findTearDown(ktClassOrObject)
    }

    override fun isIgnoredMethod(element: PsiElement?): Boolean =
        element.asKtNamedFunction()?.let(kotlinDelegate::isIgnoredMethod) ?: false

    override fun isTestMethod(element: PsiElement?): Boolean =
        element.asKtNamedFunction()?.let(kotlinDelegate::isTestMethod) ?: false

    override fun getLanguage(): Language = KotlinLanguage.INSTANCE
}

class KotlinJUnit4Framework: JUnit4Framework(), KotlinTestFrameworkAdapter {
    private val kotlinDelegateClass = JUnit4KotlinTestFramework::class.java
    private val kotlinDelegate: KotlinTestFramework by lazy { KotlinTestFramework.EXTENSION_NAME.extensionList.first { it.javaClass == kotlinDelegateClass } }

    override fun isTestClass(clazz: PsiElement): Boolean {
        val ktClassOrObject = clazz.asKtClassOrObject() ?: return false
        return kotlinDelegate.isTestClass(ktClassOrObject)
    }

    override fun findSetUpMethod(clazz: PsiElement): PsiElement? {
        val ktClassOrObject = clazz.asKtClassOrObject() ?: return null
        return kotlinDelegate.findSetUp(ktClassOrObject)
    }

    override fun findTearDownMethod(clazz: PsiElement): PsiElement? {
        val ktClassOrObject = clazz.asKtClassOrObject() ?: return null
        return kotlinDelegate.findTearDown(ktClassOrObject)
    }

    override fun isIgnoredMethod(element: PsiElement?): Boolean =
        element.asKtNamedFunction()?.let(kotlinDelegate::isIgnoredMethod) ?: false

    override fun isTestMethod(element: PsiElement?): Boolean =
        element.asKtNamedFunction()?.let(kotlinDelegate::isTestMethod) ?: false

    override fun getLanguage(): Language = KotlinLanguage.INSTANCE
}

class KotlinJUnit5Framework: JUnit5Framework(), KotlinTestFrameworkAdapter {
    private val kotlinDelegateClass = JUnit5KotlinTestFramework::class.java
    private val kotlinDelegate: KotlinTestFramework by lazy { KotlinTestFramework.EXTENSION_NAME.extensionList.first { it.javaClass == kotlinDelegateClass } }

    override fun isTestClass(clazz: PsiElement): Boolean {
        val ktClassOrObject = clazz.asKtClassOrObject() ?: return false
        return kotlinDelegate.isTestClass(ktClassOrObject)
    }

    override fun findSetUpMethod(clazz: PsiElement): PsiElement? {
        val ktClassOrObject = clazz.asKtClassOrObject() ?: return null
        return kotlinDelegate.findSetUp(ktClassOrObject)
    }

    override fun findTearDownMethod(clazz: PsiElement): PsiElement? {
        val ktClassOrObject = clazz.asKtClassOrObject() ?: return null
        return kotlinDelegate.findTearDown(ktClassOrObject)
    }

    override fun isIgnoredMethod(element: PsiElement?): Boolean =
        element.asKtNamedFunction()?.let(kotlinDelegate::isIgnoredMethod) ?: false

    override fun isTestMethod(element: PsiElement?): Boolean =
        element.asKtNamedFunction()?.let(kotlinDelegate::isTestMethod) ?: false

    override fun getLanguage(): Language = KotlinLanguage.INSTANCE
}

private fun PsiElement?.asKtClassOrObject(): KtClassOrObject? =
    when (this) {
        is KtClassOrObject -> this
        is KtLightElement<*, *> -> this.kotlinOrigin as? KtClassOrObject
        else -> null
    }

private fun PsiElement?.asKtNamedFunction(): KtNamedFunction? =
    when (this) {
        is KtNamedFunction -> this
        is KtLightMethod -> kotlinOrigin as? KtNamedFunction
        else -> null
    }
