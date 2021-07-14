package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.intentions.PackageSearchDependencyUpdateQuickFix
import com.jetbrains.packagesearch.intellij.plugin.tryDoing
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchDataService
import com.jetbrains.packagesearch.intellij.plugin.util.toUnifiedDependency

internal abstract class PackageUpdateInspection : LocalInspectionTool() {

    protected abstract fun getVersionPsiElement(file: PsiFile, dependency: UnifiedDependency): PsiElement?

    final override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (!shouldCheckFile(file)) {
            return null
        }

        val project = file.project
        val dataModel = project.packageSearchDataService.dataModelFlow.value

        if (dataModel.packageModels.isEmpty()) return null

        val module = ModuleUtil.findModuleForFile(file)
        val availableUpdates = dataModel.packagesToUpdate.updatesByModule[module] ?: return null

        val problemsHolder = ProblemsHolder(manager, file, isOnTheFly)
        for (packageUpdateInfo in availableUpdates) {
            val currentVersion = packageUpdateInfo.usageInfo.version
            val scope = packageUpdateInfo.usageInfo.scope
            val unifiedDependency = packageUpdateInfo.packageModel.toUnifiedDependency(currentVersion, scope)
            val versionElement = tryDoing { getVersionPsiElement(file, unifiedDependency) } ?: continue

            problemsHolder.registerProblem(
                versionElement,
                PackageSearchBundle.message("packagesearch.inspection.upgrade.description", packageUpdateInfo.targetVersion),
                PackageSearchDependencyUpdateQuickFix(
                    element = versionElement,
                    packageModel = packageUpdateInfo.packageModel,
                    unifiedDependency = unifiedDependency,
                    projectModule = packageUpdateInfo.usageInfo.projectModule,
                    targetVersion = packageUpdateInfo.targetVersion
                )
            )
        }

        return problemsHolder.resultsArray
    }

    private fun shouldCheckFile(file: PsiFile): Boolean =
        ProjectModuleOperationProvider.forProjectPsiFileOrNull(file.project, file)
            ?.hasSupportFor(file.project, file)
            ?: false

    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING
}
