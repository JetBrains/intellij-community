// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.runConfigurations.jvm

import com.intellij.codeInsight.runner.JavaMainMethodProvider
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.asJava.classes.KtLightClassBase
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacadeBase
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.codeInsight.findMain
import org.jetbrains.kotlin.idea.base.codeInsight.hasMain
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinMainMethodProvider: JavaMainMethodProvider {
    override fun isApplicable(clazz: PsiClass): Boolean {
        return clazz is KtLightClassBase
    }

    override fun hasMainMethod(clazz: PsiClass): Boolean {
        val lightClassBase = clazz as? KtLightClassBase
        val mainFunctionDetector = KotlinMainFunctionDetector.getInstance()
        if (lightClassBase is KtLightClassForFacadeBase) {
            return runReadAction { lightClassBase.files.any { mainFunctionDetector.hasMain(it) } }
        }
        val classOrObject = lightClassBase?.kotlinOrigin ?: return false
        return runReadAction { mainFunctionDetector.hasMain(classOrObject) }
    }

    override fun findMainInClass(clazz: PsiClass): PsiMethod? =
        runReadAction {
            val lightClassBase = clazz as? KtLightClassBase
            val mainFunctionDetector = KotlinMainFunctionDetector.getInstance()
            if (lightClassBase is KtLightClassForFacadeBase) {
                return@runReadAction lightClassBase.files
                    .asSequence()
                    .flatMap { it.declarations }
                    .mapNotNull { declaration ->
                        ProgressManager.checkCanceled()
                        when (declaration) {
                            is KtNamedFunction -> declaration.takeIf(mainFunctionDetector::isMain)
                            is KtClassOrObject -> mainFunctionDetector.findMain(declaration)
                            else -> null
                        }
                    }.flatMap { it.toLightMethods() }
                    .firstOrNull()
            }

            val classOrObject = lightClassBase?.kotlinOrigin ?: return@runReadAction null
            mainFunctionDetector.findMain(classOrObject)?.toLightMethods()?.firstOrNull()
        }
}