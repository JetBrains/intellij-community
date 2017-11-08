// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast

import com.intellij.openapi.components.ServiceManager
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.semantic.SemContributor
import com.intellij.semantic.SemKey
import com.intellij.semantic.SemRegistrar

@JvmField
val UAST_SEM_KEY = SemKey.createKey<UElement>("org.jetbrains.uast.uElement.semKey")!!

class UElementSemContributor : SemContributor() {
  override fun registerSemProviders(registrar: SemRegistrar) {
    registrar.registerSemElementProvider(UAST_SEM_KEY, PlatformPatterns.psiElement(), { sourceElement: PsiElement ->

      ServiceManager.getService(sourceElement.project, UastContext::class.java).findPlugin(sourceElement)
        ?.convertElementWithParent(sourceElement, null)
    })
  }
}