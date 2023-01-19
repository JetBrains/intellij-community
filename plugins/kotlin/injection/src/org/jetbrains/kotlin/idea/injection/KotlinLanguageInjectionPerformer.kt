// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.injection

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.lang.injection.general.Injection
import com.intellij.lang.injection.general.LanguageInjectionPerformer
import com.intellij.psi.PsiElement
import org.intellij.plugins.intelliLang.inject.InjectorUtils
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

class KotlinLanguageInjectionPerformer : LanguageInjectionPerformer {
    override fun isPrimary(): Boolean = true

    override fun performInjection(registrar: MultiHostRegistrar, injection: Injection, context: PsiElement): Boolean {
        if (context !is KtElement || !isSupportedElement(context)) return false

        val support = InjectorUtils.getActiveInjectionSupports()
            .firstIsInstanceOrNull<KotlinLanguageInjectionSupport>() ?: return false

        val language = InjectorUtils.getLanguageByString(injection.injectedLanguageId) ?: return false

        val file = context.containingKtFile
        val parts = transformToInjectionParts(injection, context) ?: return false

        if (parts.ranges.isEmpty()) return false

        InjectorUtils.registerInjection(language, file, parts.ranges, registrar)
        InjectorUtils.registerSupport(support, false, context, language)
        InjectorUtils.putInjectedFileUserData(
            context,
            language,
            InjectedLanguageManager.FRANKENSTEIN_INJECTION,
            if (parts.isUnparsable) java.lang.Boolean.TRUE else null
        )

        return true
    }
}