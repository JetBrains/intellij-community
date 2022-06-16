package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectService

abstract class PackageVersionRangeInspection : AbstractPackageUpdateInspectionCheck() {

    override fun ProblemsHolder.checkFile(file: PsiFile, fileModule: Module) {
        file.project.packageSearchProjectService.dependenciesByModuleStateFlow.value
            .entries
            .find { it.key.nativeModule == fileModule }
            ?.value
            ?.filter { dependency -> dependency.coordinates.version?.let { isIvyRange(it) } ?: false }
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

    private fun isIvyRange(version: String): Boolean {
        // See https://ant.apache.org/ivy/history/2.1.0/ivyfile/dependency.html
        val normalizedVersion = version.trimEnd()
        if (normalizedVersion.endsWith('+')) return true

        if (normalizedVersion.startsWith("latest.")) return true

        val startsWithParenthesisOrBrackets = normalizedVersion.startsWith('(') || normalizedVersion.startsWith('[')
        val endsWithParenthesisOrBrackets = normalizedVersion.endsWith(')') || normalizedVersion.endsWith(']')
        if (startsWithParenthesisOrBrackets && endsWithParenthesisOrBrackets) return true

        return false
    }
}
