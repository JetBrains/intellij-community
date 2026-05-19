// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.injection

import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.injected.InjectionBackgroundSuppressor
import com.intellij.python.community.impl.conda.environmentYml.CondaEnvironmentYmlSdkUtils.envFileNames
import com.jetbrains.python.requirements.RequirementsLanguage
import org.jetbrains.annotations.Unmodifiable
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence

internal class CondaRequirementsLanguageInjector : MultiHostInjector {
  override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
    val pipSequence = (context as? YAMLScalar)?.parent?.parent as? YAMLSequence ?: return
    val pipKeyValue = pipSequence.parent as? YAMLKeyValue ?: return

    if (pipKeyValue.keyText != "pip") {
      return
    }

    val depsKeyValue = pipKeyValue.parent?.parent?.parent?.parent as? YAMLKeyValue ?: return

    if (depsKeyValue.keyText != "dependencies") {
      return
    }

    val file = depsKeyValue.parent?.parent?.parent as? YAMLFile ?: return

    if (file.name !in envFileNames) {
      return
    }

    val injectionHost = (context as PsiLanguageInjectionHost).also {
      it.putUserData(InjectionBackgroundSuppressor.SUPPRESS_INJECTION_BACKGROUND, Unit)
    }

    registrar
      .startInjecting(RequirementsLanguage.INSTANCE)
      .addPlace(null, null, injectionHost, TextRange.create(0, injectionHost.textLength))
      .doneInjecting()
  }

  override fun elementsToInjectIn(): @Unmodifiable List<Class<out PsiElement?>?> =
    listOf(YAMLScalar::class.java)
}
