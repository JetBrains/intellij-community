package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageVersionRangeInspection

internal class GradlePackageVersionRangeInspection : PackageVersionRangeInspection() {

    override fun getStaticDescription(): String = PackageSearchBundle.getMessage("packagesearch.inspection.range.description.gradle")

    override fun getVersionPsiElement(file: PsiFile, dependency: UnifiedDependency) = getGradleVersionPsiElement(dependency, file)
}
