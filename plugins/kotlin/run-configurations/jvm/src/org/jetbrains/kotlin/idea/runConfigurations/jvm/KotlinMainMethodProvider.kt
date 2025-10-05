// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.runConfigurations.jvm

import com.intellij.codeInsight.runner.JavaMainMethodProvider
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.parentOfType
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.asJava.classes.KtExtensibleLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.codeInsight.findMain
import org.jetbrains.kotlin.idea.base.codeInsight.hasMain
import org.jetbrains.kotlin.idea.run.KotlinRunConfigurationProducer
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinMainMethodProvider : JavaMainMethodProvider {
  override fun isDumbAware(): Boolean = true

  @RequiresReadLock
  override fun isApplicable(clazz: PsiClass): Boolean {
    return clazz is KtLightClass
  }

  @RequiresReadLock
  override fun hasMainMethod(clazz: PsiClass): Boolean {
    val mainFunctionDetector = KotlinMainFunctionDetector.getInstanceDumbAware(clazz.project)

    return when (clazz) {
      is KtLightClassForFacade -> clazz.files.any(mainFunctionDetector::hasMain)
      is KtExtensibleLightClass -> {
        val classOrObject = clazz.kotlinOrigin ?: return false
        mainFunctionDetector.hasMain(classOrObject)
      }
      else -> false
    }
  }

  @RequiresReadLock
  override fun findMainInClass(clazz: PsiClass): PsiMethod? {
    return try {
      val mainFunctionDetector = KotlinMainFunctionDetector.getInstanceDumbAware(clazz.project)

      val ktMainMethod = when (clazz) {
        is KtLightClassForFacade -> clazz.files.firstNotNullOfOrNull(mainFunctionDetector::findMain)
        is KtExtensibleLightClass -> {
          val classOrObject = clazz.kotlinOrigin ?: return null
          mainFunctionDetector.findMain(classOrObject)
        }
        else -> null
      }

      ktMainMethod?.toLightMethods()?.firstOrNull()
    }
    catch (e: IndexNotReadyException) {
      null
    }
  }

  @Deprecated("Deprecated in Java")
  @RequiresReadLock
  override fun getMainClassName(clazz: PsiClass): String? {
    return when (clazz) {
      is KtLightClassForFacade -> clazz.facadeClassFqName.asString()
      is KtExtensibleLightClass -> {
        val classOrObject = clazz.kotlinOrigin ?: return null
        KotlinRunConfigurationProducer.getMainClassJvmName(classOrObject)
      }
      else -> null
    }
  }

  @RequiresReadLock
  override fun getMainClassQualifiedName(clazz: PsiClass): String? {
    return when (clazz) {
      is KtLightClassForFacade -> clazz.facadeClassFqName.asString()
      is KtExtensibleLightClass -> {
        val classOrObject = clazz.kotlinOrigin ?: return null
        KotlinRunConfigurationProducer.getMainClassQualifiedName(classOrObject)
      }
      else -> null
    }
  }


  @RequiresReadLock
  override fun isMain(psiElement: PsiElement): Boolean {
    val ktNamedFunction = psiElement.parentOfType<KtNamedFunction>() ?: return false

    val mainFunctionDetector = KotlinMainFunctionDetector.getInstanceDumbAware(psiElement.project)
    return mainFunctionDetector.isMain(ktNamedFunction)
  }
}