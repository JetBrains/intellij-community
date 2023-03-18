// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.junit

import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.lang.Language
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiElement
import com.intellij.testIntegration.JvmTestFramework
import com.intellij.testIntegration.TestFramework
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFramework
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import javax.swing.Icon

abstract class AbstractKotlinTestFrameworkAdapter(
    private val kotlinDelegateClass: Class<out KotlinTestFramework>,
    private val javaDelegateClass: Class<out JvmTestFramework>
) : JvmTestFramework {
    private val kotlinDelegate: KotlinTestFramework by lazy { KotlinTestFramework.EXTENSION_NAME.extensionList.first { it.javaClass == kotlinDelegateClass } }
    private val javaDelegate: JvmTestFramework by lazy {
        TestFramework.EXTENSION_NAME.extensionList.first { it.javaClass == javaDelegateClass } as JvmTestFramework
    }

    override fun getName(): String = javaDelegate.name

    override fun getIcon(): Icon = javaDelegate.icon

    override fun isLibraryAttached(module: Module): Boolean = javaDelegate.isLibraryAttached(module)

    override fun getFrameworkLibraryDescriptor(): ExternalLibraryDescriptor = javaDelegate.frameworkLibraryDescriptor

    override fun getLibraryPath(): String? = javaDelegate.libraryPath

    override fun getDefaultSuperClass(): String? = javaDelegate.defaultSuperClass

    override fun isTestClass(clazz: PsiElement): Boolean {
        val ktClassOrObject = clazz.asKtClassOrObject() ?: return false
        return kotlinDelegate.isTestClass(ktClassOrObject)
    }

    override fun isPotentialTestClass(clazz: PsiElement): Boolean {
        val vFile = clazz.containingFile.virtualFile ?: return false
        if (clazz.asKtClassOrObject() == null) return false
        return ProjectRootManager.getInstance(clazz.project).fileIndex.isInTestSourceContent(vFile)
    }

    override fun findSetUpMethod(clazz: PsiElement): PsiElement? {
        val ktClassOrObject = clazz.asKtClassOrObject() ?: return null
        return kotlinDelegate.findSetUp(ktClassOrObject)
    }

    override fun findTearDownMethod(clazz: PsiElement): PsiElement? {
        val ktClassOrObject = clazz.asKtClassOrObject() ?: return null
        return kotlinDelegate.findTearDown(ktClassOrObject)
    }

    override fun findOrCreateSetUpMethod(clazz: PsiElement): PsiElement? {
        // TODO:
        return javaDelegate.findOrCreateSetUpMethod(clazz)
    }

    override fun getSetUpMethodFileTemplateDescriptor(): FileTemplateDescriptor =
        javaDelegate.setUpMethodFileTemplateDescriptor

    override fun getTearDownMethodFileTemplateDescriptor(): FileTemplateDescriptor =
        javaDelegate.tearDownMethodFileTemplateDescriptor

    override fun getTestMethodFileTemplateDescriptor(): FileTemplateDescriptor =
        javaDelegate.testMethodFileTemplateDescriptor

    override fun isIgnoredMethod(element: PsiElement?): Boolean =
      element.asKtNamedFunction()?.let(kotlinDelegate::isIgnoredMethod) ?: false

    override fun isTestMethod(element: PsiElement?): Boolean =
      element.asKtNamedFunction()?.let(kotlinDelegate::isTestMethod) ?: false

    override fun getLanguage(): Language = KotlinLanguage.INSTANCE

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
}