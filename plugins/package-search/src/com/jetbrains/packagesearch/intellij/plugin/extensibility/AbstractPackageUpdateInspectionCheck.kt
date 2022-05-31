package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectService

abstract class AbstractPackageUpdateInspectionCheck : LocalInspectionTool() {

    protected open fun shouldCheckFile(file: PsiFile): Boolean = false

    protected open fun selectPsiElementIndex(dependencyDeclarationIndexes: DependencyDeclarationIndexes): Int? = null

    final override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> {
        val isFileNotTracked by lazy {
            file.virtualFile !in file.project.packageSearchProjectService
                .projectModulesStateFlow
                .value
                .map { it.buildFile }
        }
        val shouldNotCheckFile by lazy { !shouldCheckFile(file) }
        val isNotAvailable = !file.project.packageSearchProjectService.isAvailable
        if (isNotAvailable || isFileNotTracked || shouldNotCheckFile) {
            return emptyArray()
        }

        val fileModule = ModuleUtil.findModuleForFile(file)
        if (fileModule == null) {
            thisLogger().warn("Inspecting file belonging to an unknown module")
            return emptyArray()
        }

        val problemsHolder = ProblemsHolder(manager, file, isOnTheFly)

        problemsHolder.checkFile(file, fileModule)

        return problemsHolder.resultsArray
    }

    abstract fun ProblemsHolder.checkFile(file: PsiFile, fileModule: Module)
}
