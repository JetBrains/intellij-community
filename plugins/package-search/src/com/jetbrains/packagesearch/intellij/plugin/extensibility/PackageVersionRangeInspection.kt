package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtil
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
            ?.filter { it.dependency.coordinates.version?.let { isRange(it) } ?: false }
            ?.mapNotNull { coordinates ->
                runCatching {
                    coordinates.declarationIndexes
                        ?.let { selectPsiElementIndex(it) }
                        ?.let { PsiUtil.getElementAtOffset(file, it) }
                }.getOrNull()
                    ?.let { coordinates to it }
            }
            ?.forEach { (coordinatesWithResolvedVersion, psiElement) ->

                val message = coordinatesWithResolvedVersion.dependency.coordinates.version
                    ?.let { PackageSearchBundle.message("packagesearch.inspection.upgrade.range.withVersion", it) }
                    ?: PackageSearchBundle.message("packagesearch.inspection.upgrade.range")

                registerProblem(psiElement, message, ProblemHighlightType.WEAK_WARNING)
            }
    }
}