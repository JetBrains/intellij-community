package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyDeclarationIndexes
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageUpdateInspection

internal class GradlePackageUpdateInspection : PackageUpdateInspection() {

    override fun getStaticDescription(): String = PackageSearchBundle.getMessage("packagesearch.inspection.upgrade.description.gradle")
    override fun selectPsiElementIndex(dependencyDeclarationIndexes: DependencyDeclarationIndexes) =
        dependencyDeclarationIndexes.coordinatesStartIndex

    override fun shouldCheckFile(file: PsiFile): Boolean =
        GradleProjectModuleOperationProvider.hasSupportFor(file)
}
