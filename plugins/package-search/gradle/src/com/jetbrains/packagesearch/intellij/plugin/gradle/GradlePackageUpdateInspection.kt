package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageUpdateInspection

internal class GradlePackageUpdateInspection : PackageUpdateInspection() {

    override fun getStaticDescription(): String = PackageSearchBundle.getMessage("packagesearch.inspection.upgrade.description.gradle")

    override fun getVersionPsiElement(file: PsiFile, dependency: UnifiedDependency): PsiElement? {
        val groupId = dependency.coordinates.groupId ?: return null
        val artifactId = dependency.coordinates.artifactId ?: return null
        return GradleModuleTransformer.findDependencyElement(file, groupId, artifactId)
    }
}
