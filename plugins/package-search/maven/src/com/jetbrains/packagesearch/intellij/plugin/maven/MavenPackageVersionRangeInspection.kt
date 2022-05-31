package com.jetbrains.packagesearch.intellij.plugin.maven

import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyDeclarationIndexes
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageVersionRangeInspection

internal class MavenPackageVersionRangeInspection : PackageVersionRangeInspection() {

    override fun getStaticDescription(): String = PackageSearchBundle.getMessage("packagesearch.inspection.range.description.maven")
    override fun selectPsiElementIndex(dependencyDeclarationIndexes: DependencyDeclarationIndexes) =
        dependencyDeclarationIndexes.versionStartIndex

    override fun shouldCheckFile(file: PsiFile) =
        MavenProjectModuleOperationProvider.hasSupportFor(file.project, file)
}
