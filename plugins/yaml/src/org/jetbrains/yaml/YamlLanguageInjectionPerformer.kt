// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml

import com.intellij.injected.editor.InjectionMeta
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.lang.injection.general.Injection
import com.intellij.lang.injection.general.LanguageInjectionPerformer
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.impl.YAMLBlockScalarImpl
import org.jetbrains.yaml.psi.impl.YAMLScalarImpl

class YamlLanguageInjectionPerformer : LanguageInjectionPerformer {
  override fun isPrimary(): Boolean {
    return true
  }

  override fun performInjection(registrar: MultiHostRegistrar,
                                injection: Injection,
                                context: PsiElement): Boolean {
    if (context !is YAMLScalar) return false
    val language = injection.injectedLanguage ?: return false
    registrar.startInjecting(language)
    if (context is YAMLScalarImpl) {

      val contentRanges = context.contentRanges

      if(context is YAMLBlockScalarImpl){
        context.putUserData(InjectionMeta.INJECTION_INDENT, " ".repeat(context.locateIndent()))
        context.putUserData(InjectionMeta.SUPPRESS_COPY_PASTE_HANDLER_IN_FE, true)
      }
      
      if (contentRanges.isEmpty()) {
        // do nothing
      }
      else if (contentRanges.size == 1) {
        registrar.addPlace(injection.prefix, injection.suffix, context, contentRanges.single())
      }
      else {
        registrar.addPlace(injection.prefix, null, context, contentRanges.first())
        for (textRange in contentRanges.subList(1, contentRanges.size - 1)) {
          registrar.addPlace(null, null, context, textRange)
        }
        registrar.addPlace(null, injection.suffix, context, contentRanges.last())
      }

    }
    else {
      val textRange = ElementManipulators.getValueTextRange(context)
      registrar.addPlace(injection.prefix, injection.suffix, (context as PsiLanguageInjectionHost), textRange)
    }
    registrar.doneInjecting()
    return true
  }
}
