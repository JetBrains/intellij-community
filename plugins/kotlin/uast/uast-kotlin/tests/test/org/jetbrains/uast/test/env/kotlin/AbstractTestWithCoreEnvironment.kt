// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.env.kotlin

import com.intellij.core.CoreApplicationEnvironment
import com.intellij.mock.MockProject
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNameHelper
import com.intellij.psi.impl.PsiNameHelperImpl
import com.intellij.rt.execution.junit.FileComparisonFailure
import junit.framework.TestCase
import org.jetbrains.uast.UastContext
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.evaluation.UEvaluatorExtension
import org.jetbrains.uast.java.JavaUastLanguagePlugin
import java.io.File

abstract class AbstractTestWithCoreEnvironment : TestCase() {
    private var myEnvironment: AbstractCoreEnvironment? = null

    protected val environment: AbstractCoreEnvironment
        get() = myEnvironment!!

    protected val project: MockProject
        get() = environment.project

    protected val uastContext: org.jetbrains.uast.UastContext by lazy {
        com.intellij.openapi.components.ServiceManager.getService(project, org.jetbrains.uast.UastContext::class.java)
    }

    protected val psiManager: PsiManager by lazy {
        PsiManager.getInstance(project)
    }

    override fun tearDown() {
        disposeEnvironment()
    }

    protected abstract fun createEnvironment(source: File): AbstractCoreEnvironment

    protected fun initializeEnvironment(source: File) {
        if (myEnvironment != null) {
            error("Environment is already initialized")
        }

        myEnvironment = createEnvironment(source)
        CoreApplicationEnvironment.registerApplicationExtensionPoint(
            UastLanguagePlugin.extensionPointName,
            UastLanguagePlugin::class.java
        )

        CoreApplicationEnvironment.registerApplicationExtensionPoint(
            UEvaluatorExtension.EXTENSION_POINT_NAME,
            UEvaluatorExtension::class.java
        )

        project.registerService(PsiNameHelper::class.java, PsiNameHelperImpl(project))
        project.registerService(UastContext::class.java)
        registerUastLanguagePlugins()
    }

    private fun registerUastLanguagePlugins() {
        Extensions.getRootArea().getExtensionPoint(UastLanguagePlugin.extensionPointName).registerExtension(JavaUastLanguagePlugin())
    }

    protected fun disposeEnvironment() {
        myEnvironment?.dispose()
        myEnvironment = null
    }
}
