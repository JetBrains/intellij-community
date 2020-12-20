package com.jetbrains.packagesearch.intellij.plugin.intentions

import com.intellij.psi.PsiJavaCodeReferenceElement

class JavaPackageSearchQuickFixProvider : PackageSearchQuickFixProvider<PsiJavaCodeReferenceElement>() {

    override fun getReferenceClass() = PsiJavaCodeReferenceElement::class.java
}
