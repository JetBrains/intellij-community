package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectService

abstract class PackageVersionRangeInspection : AbstractPackageUpdateInspectionCheck() {

    companion object {

        private fun isRange(version: String) = version.any { !it.isLetter() && !it.isDigit() && it != '_' && it != '.' && it != '-' }
    }

    override fun ProblemsHolder.checkFile(file: PsiFile, fileModule: Module) {
        file.project.packageSearchProjectService.dependenciesByModuleStateFlow.value
            .entries
            .find { it.key.nativeModule == fileModule }
            ?.value
            ?.filter { dependency -> dependency.coordinates.version?.let { isRange(it) } ?: false }
            ?.mapNotNull { coordinates ->
                runCatching { getVersionPsiElement(file, coordinates) }.getOrNull()
                    ?.let { coordinates to it }
            }
            ?.forEach { (dependency, psiElement) ->

                val message = dependency.coordinates.version
                    ?.let { PackageSearchBundle.message("packagesearch.inspection.upgrade.range.withVersion", it) }
                    ?: PackageSearchBundle.message("packagesearch.inspection.upgrade.range")

                registerProblem(psiElement, message, ProblemHighlightType.WEAK_WARNING)
            }
    }
}