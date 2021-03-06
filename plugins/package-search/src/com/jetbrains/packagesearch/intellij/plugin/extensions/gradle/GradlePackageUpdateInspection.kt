package com.jetbrains.packagesearch.intellij.plugin.extensions.gradle

import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Package
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageUpdateInspection
import org.jetbrains.plugins.gradle.util.GradleConstants.DEFAULT_SCRIPT_NAME
import org.jetbrains.plugins.gradle.util.GradleConstants.KOTLIN_DSL_SCRIPT_NAME

class GradlePackageUpdateInspection : PackageUpdateInspection() {

    override fun getStaticDescription(): String? = PackageSearchBundle.getMessage("packagesearch.inspection.update.description.gradle")

    override fun shouldCheckFile(file: PsiFile): Boolean = file.name.let { it == DEFAULT_SCRIPT_NAME || it == KOTLIN_DSL_SCRIPT_NAME }

    override fun getVersionElement(file: PsiFile, dependency: StandardV2Package) =
        GradleProjectModuleProvider.findDependencyElement(file, dependency.groupId, dependency.artifactId)
}
