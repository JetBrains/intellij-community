package com.jetbrains.packagesearch.intellij.plugin.intentions

import com.intellij.psi.PsiJavaCodeReferenceElement

class JavaPackageSearchUnresolvedReferenceQuickFixProvider : PackageSearchUnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement>() {

    override fun getReferenceClass() = PsiJavaCodeReferenceElement::class.java
}
