package org.toml.ide.resolve

import com.intellij.openapi.paths.GlobalPathReferenceProvider
import com.intellij.openapi.paths.WebReference
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.*
import com.intellij.psi.PsiReference
import com.intellij.util.ProcessingContext
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

internal class TomlReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            psiElement(TomlLiteral::class.java),
            TomlWebReferenceProvider(),
            PsiReferenceRegistrar.LOWER_PRIORITY
        )
    }
}

private class TomlWebReferenceProvider : PsiReferenceProvider() {

    // web references do not point to any real PsiElement
    override fun acceptsTarget(target: PsiElement): Boolean = false

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val kind = (element as? TomlLiteral)?.kind as? TomlLiteralKind.String ?: return PsiReference.EMPTY_ARRAY
        if (!element.textContains(':')) return PsiReference.EMPTY_ARRAY
        val textValue = kind.value ?: return PsiReference.EMPTY_ARRAY

        return if (GlobalPathReferenceProvider.isWebReferenceUrl(textValue)) {
            arrayOf(WebReference(element, textValue))
        } else {
            PsiReference.EMPTY_ARRAY
        }
    }
}
