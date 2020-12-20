package com.jetbrains.packagesearch.intellij.plugin.intentions

import org.jetbrains.kotlin.idea.references.KtSimpleNameReference

class KotlinPackageSearchQuickFixProvider : PackageSearchQuickFixProvider<KtSimpleNameReference>() {

    override fun getReferenceClass() = KtSimpleNameReference::class.java
}
