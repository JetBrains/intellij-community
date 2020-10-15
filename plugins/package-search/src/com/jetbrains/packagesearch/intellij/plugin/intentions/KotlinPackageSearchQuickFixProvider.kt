package com.jetbrains.packagesearch.intellij.plugin.intentions

import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.psi.PsiReference

class KotlinPackageSearchQuickFixProvider : PackageSearchQuickFixProvider<PsiReference>() {

    override fun getReferenceClass(): Class<PsiReference> = try {
        @Suppress("UNCHECKED_CAST") // Caught and handled below
        Class.forName("org.jetbrains.kotlin.idea.references.KtSimpleNameReference") as Class<PsiReference>
    } catch (e: ClassNotFoundException) {
        throw RuntimeExceptionWithAttachments("Trying to use Kotlin features, but the Kotlin plugin doesn't seem to be enabled.", e)
    }
}
