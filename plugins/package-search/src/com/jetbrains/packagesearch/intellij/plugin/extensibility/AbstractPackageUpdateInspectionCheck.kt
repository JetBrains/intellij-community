package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectService

abstract class AbstractPackageUpdateInspectionCheck : LocalInspectionTool() {

    companion object {

        private fun shouldCheckFile(file: PsiFile): Boolean {
            if (!file.project.packageSearchProjectService.isAvailable) return false

            val provider = ProjectModuleOperationProvider.forProjectPsiFileOrNull(file.project, file)
                ?.takeIf { it.usesSharedPackageUpdateInspection() }
                ?: return false

            return provider.hasSupportFor(file.project, file)
        }
    }

    protected abstract fun getVersionPsiElement(file: PsiFile, dependency: UnifiedDependency): PsiElement?

    final override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (!shouldCheckFile(file)) {
            return null
        }

        val fileModule = ModuleUtil.findModuleForFile(file)
        if (fileModule == null) {
            thisLogger().warn("Inspecting file belonging to an unknown module")
            return null
        }

        val problemsHolder = ProblemsHolder(manager, file, isOnTheFly)

        problemsHolder.checkFile(file, fileModule)

        return problemsHolder.resultsArray
    }

    abstract fun ProblemsHolder.checkFile(file: PsiFile, fileModule: Module)
}