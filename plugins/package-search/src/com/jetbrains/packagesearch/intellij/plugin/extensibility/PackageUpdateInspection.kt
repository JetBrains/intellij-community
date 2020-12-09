package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.text.SemVer
import com.intellij.util.text.VersionComparatorUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Package
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Version
import com.jetbrains.packagesearch.intellij.plugin.intentions.PackageUpdateQuickFix
import com.jetbrains.packagesearch.intellij.plugin.looksLikeGradleVariable
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.PackageSearchToolWindowFactory
import com.jetbrains.packagesearch.intellij.plugin.version.looksLikeStableVersion

abstract class PackageUpdateInspection : LocalInspectionTool() {

    abstract fun shouldCheckFile(file: PsiFile): Boolean

    abstract fun getVersionElement(file: PsiFile, dependency: StandardV2Package): PsiElement?

    final override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (!shouldCheckFile(file)) {
            return null
        }

        val project = file.project
        val model = project.getUserData(PackageSearchToolWindowFactory.ToolWindowModelKey) ?: return null
        val module = ProjectModuleProvider.obtainAllProjectModulesFor(project).toList().find {
            it.buildFile == file.virtualFile
        } ?: return null
        val problemsHolder = ProblemsHolder(manager, file, isOnTheFly)

        model.preparePackageOperationTargetsFor(listOf(module), null).forEach { target ->
            val dependency = target.packageSearchDependency.remoteInfo ?: return@forEach
            val highestVersion = (dependency.versions?.map(StandardV2Version::version) ?: emptyList())
                .filter { it.isNotBlank() && !looksLikeGradleVariable(it) && (looksLikeStableVersion(it)) }
                .distinct()
                .sortedWith(Comparator { o1, o2 -> VersionComparatorUtil.compare(o2, o1) }).firstOrNull() ?: return@forEach

            val semVerHighest = SemVer.parseFromText(highestVersion)
            val semVerTarget = SemVer.parseFromText(target.version)
            if (semVerHighest != null && semVerTarget != null && semVerHighest > semVerTarget) {
                val versionElement = getVersionElement(file, dependency) ?: return@forEach
                problemsHolder.registerProblem(
                    versionElement,
                    PackageSearchBundle.message("packagesearch.inspection.update.description", highestVersion),
                    PackageUpdateQuickFix(versionElement, target, dependency, highestVersion)
                )
            }
        }

        return problemsHolder.resultsArray
    }

    override fun getDefaultLevel() = HighlightDisplayLevel.WARNING!!
}
