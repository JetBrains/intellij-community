// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml

import com.intellij.injected.editor.InjectionMeta
import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.lang.injection.general.Injection
import com.intellij.lang.injection.general.LanguageInjectionPerformer
import com.intellij.openapi.util.TextRange
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
    injectIntoYamlMultiRanges(registrar, context, getYamlLiteralRanges(context), language, injection.prefix, injection.suffix)
    return true
  }

}

fun getYamlLiteralRanges(context: PsiElement) =
  (context as? YAMLScalarImpl)?.contentRanges ?: listOf(ElementManipulators.getValueTextRange(context))

fun injectIntoYamlMultiRanges(registrar: MultiHostRegistrar,
                              context: PsiLanguageInjectionHost,
                              ranges: List<TextRange>,
                              language: Language,
                              prefix: String?,
                              suffix: String?) {
  if (ranges.isEmpty()) return

  registrar.startInjecting(language)

  if (context is YAMLBlockScalarImpl)
    context.putUserData(InjectionMeta.getInjectionIndent(), context.indentString)

  if (ranges.size == 1) {
    registrar.addPlace(prefix, suffix, context, ranges.single())
  }
  else {
    registrar.addPlace(prefix, null, context, ranges.first())
    for (textRange in ranges.subList(1, ranges.size - 1)) {
      registrar.addPlace(null, null, context, textRange)
    }
    registrar.addPlace(null, suffix, context, ranges.last())
  }
  registrar.doneInjecting()
}

