// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.injection

import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.jetbrains.python.requirements.RequirementsLanguage
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable

class TomlRequirementsLanguageInjector : MultiHostInjector {
  override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
    val tomlKeyValue = (context as TomlLiteral).parent.parent as? TomlKeyValue ?: return
    val table = tomlKeyValue.parent as? TomlTable ?: return

    val fieldName = tomlKeyValue.key.text
    val sectionName = table.header.key?.text ?: return

    if (!TomlRequirementsInjectionSupport.isSupported(sectionName, fieldName))
      return

    val injectionHost = context as PsiLanguageInjectionHost
    val textRange = TextRange.create(1, injectionHost.textLength - 1)
    registrar
      .startInjecting(RequirementsLanguage.Companion.INSTANCE)
      .addPlace(null, null, injectionHost, textRange)
      .doneInjecting()
  }

  override fun elementsToInjectIn(): List<Class<out PsiElement>> {
    try {
      return listOf(TomlLiteral::class.java)
    }
    catch (_: NoClassDefFoundError) {
      logger<TomlRequirementsLanguageInjector>().warn("Failed to inject Requirements language into TomlLiteral")
      return listOf()
    }
  }
}