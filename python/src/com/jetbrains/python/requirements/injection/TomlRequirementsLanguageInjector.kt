// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.injection

import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.injected.InjectionBackgroundSuppressor
import com.intellij.python.requirements.RequirementsLanguage
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

    val injectionHost = (context as PsiLanguageInjectionHost).also {
      it.putUserData(InjectionBackgroundSuppressor.SUPPRESS_INJECTION_BACKGROUND, Unit)
    }
    val textRange = requirementsContentRange(injectionHost.text) ?: return

    registrar
      .startInjecting(RequirementsLanguage)
      .addPlace(null, null, injectionHost, textRange)
      .doneInjecting()
  }

  companion object {
    /**
     * Content range of a TOML string literal, excluding its delimiters: one char for the single
     * `"…"` / `'…'` forms and three for the triple-quoted multiline forms `"""…"""` / `'''…'''`.
     * Returns null for an empty / too-short / unterminated literal so nothing gets injected.
     */
    @JvmStatic
    fun requirementsContentRange(text: CharSequence): TextRange? {
      val delimiter = when {
        text.length >= 6 && text.startsWith("\"\"\"") && text.endsWith("\"\"\"") -> 3
        text.length >= 6 && text.startsWith("'''") && text.endsWith("'''") -> 3
        text.length >= 2 && text.startsWith("\"") && text.endsWith("\"") -> 1
        text.length >= 2 && text.startsWith("'") && text.endsWith("'") -> 1
        else -> return null // unterminated / malformed while typing — inject nothing
      }
      val end = text.length - delimiter
      return if (end > delimiter) TextRange.create(delimiter, end) else null
    }
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