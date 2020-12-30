package org.jetbrains.kotlin.idea.injection

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.lang.injection.general.Injection
import com.intellij.lang.injection.general.LanguageInjectionPerformer
import com.intellij.psi.PsiElement
import org.intellij.plugins.intelliLang.inject.InjectorUtils
import org.intellij.plugins.intelliLang.inject.config.BaseInjection
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

class KotlinLanguageInjectionPerformer : LanguageInjectionPerformer {
    override fun isPrimary(): Boolean = true

    override fun performInjection(registrar: MultiHostRegistrar, injection: Injection, context: PsiElement): Boolean {
        val ktHost: KtStringTemplateExpression = context as? KtStringTemplateExpression ?: return false
        if (!context.isValidHost) return false

        val support = InjectorUtils.getActiveInjectionSupports()
            .firstIsInstanceOrNull<KotlinLanguageInjectionSupport>() ?: return false

        val language = InjectorUtils.getLanguageByString(injection.injectedLanguageId) ?: return false

        if (ktHost.hasInterpolation()) {
            val file = ktHost.containingKtFile
            val parts = splitLiteralToInjectionParts(injection, ktHost) ?: return false

            if (parts.ranges.isEmpty()) return false

            InjectorUtils.registerInjection(language, parts.ranges, file, registrar)
            InjectorUtils.registerSupport(support, false, ktHost, language)
            InjectorUtils.putInjectedFileUserData(
                ktHost,
                language,
                InjectedLanguageManager.FRANKENSTEIN_INJECTION,
                if (parts.isUnparsable) java.lang.Boolean.TRUE else null
            )
        } else {
            if (injection is BaseInjection)
            //TODO: it is not a safe check, `registerInjectionSimple` should be rewritten or avoided
                InjectorUtils.registerInjectionSimple(ktHost, injection, support, registrar)
            else {
                return false
            }
        }

        return true
    }
}