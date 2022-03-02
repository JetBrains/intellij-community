package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageUpdateInspection
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageVersionRangeInspection

private fun getGradleVersionPsiElement(
    dependency: UnifiedDependency,
    file: PsiFile
): PsiElement? {
    val groupId = dependency.coordinates.groupId ?: return null
    val artifactId = dependency.coordinates.artifactId ?: return null
    return GradleModuleTransformer.findDependencyElement(file, groupId, artifactId)
}

internal class GradlePackageUpdateInspection : PackageUpdateInspection() {

    override fun getStaticDescription(): String = PackageSearchBundle.getMessage("packagesearch.inspection.upgrade.description.gradle")

    override fun getVersionPsiElement(file: PsiFile, dependency: UnifiedDependency): PsiElement? = getGradleVersionPsiElement(dependency, file)
}

internal class GradlePackageVersionRangeInspection : PackageVersionRangeInspection() {

    override fun getStaticDescription(): String = PackageSearchBundle.getMessage("packagesearch.inspection.range.description.gradle")

    override fun getVersionPsiElement(file: PsiFile, dependency: UnifiedDependency) = getGradleVersionPsiElement(dependency, file)
}
