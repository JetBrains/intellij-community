package com.jetbrains.packagesearch.intellij.plugin.intentions

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiUtil

abstract class PackageSearchQuickFixProvider<T : PsiReference> : UnresolvedReferenceQuickFixProvider<T>() {

    override fun registerFixes(ref: T, registrar: QuickFixActionRegistrar) {
        if (!PsiUtil.isModuleFile(ref.element.containingFile)) {
            registrar.register(PackageSearchQuickFix(ref))
        }
    }
}
