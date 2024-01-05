// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.requirements

import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable

class RequirementsLanguageInjector : MultiHostInjector {
  override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
    val parent = (context as TomlLiteral).parent.parent
    if (parent is TomlKeyValue) {
      if (parent.key.text == "dependencies" && "project" == (parent.parent as? TomlTable)?.header?.key?.text
          || parent.key.text == "requires" && "build-system" == (parent.parent as? TomlTable)?.header?.key?.text) {
        registrar.startInjecting(RequirementsLanguage.INSTANCE).addPlace(null, null, context as PsiLanguageInjectionHost,
                                                                         TextRange.create(1,
                                                                                          (context as PsiLanguageInjectionHost).textLength - 1)).doneInjecting()
      }
    }
  }

  override fun elementsToInjectIn(): MutableList<out Class<out PsiElement>> {
    try {
      return mutableListOf(TomlLiteral::class.java)
    }
    catch (exception: NoClassDefFoundError) {
      return mutableListOf()
    }
  }
}