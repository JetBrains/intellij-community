package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyDeclarationIndexes
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageVersionRangeInspection

internal class GradlePackageVersionRangeInspection : PackageVersionRangeInspection() {

    override fun getStaticDescription(): String = PackageSearchBundle.getMessage("packagesearch.inspection.range.description.gradle")

    override fun selectPsiElementIndex(dependencyDeclarationIndexes: DependencyDeclarationIndexes) =
        dependencyDeclarationIndexes.coordinatesStartIndex

    override fun shouldCheckFile(file: PsiFile) =
        GradleProjectModuleOperationProvider.hasSupportFor(file)
}
