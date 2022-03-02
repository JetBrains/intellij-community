package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ui.ListEditForm
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.intentions.PackageSearchDependencyUpgradeQuickFix
import com.jetbrains.packagesearch.intellij.plugin.tryDoing
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageIdentifier
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.NotifyingOperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectService
import com.jetbrains.packagesearch.intellij.plugin.util.toUnifiedDependency
import org.jetbrains.annotations.Nls
import javax.swing.JPanel

/**
 * An inspection that flags out-of-date dependencies in supported files, supplying a quick-fix to
 * upgrade them to the latest version.
 *
 * Implementations of build system integrations that wish to opt-in to this inspection + quick-fix
 * infrastructure need to override [ProjectModuleOperationProvider.usesSharedPackageUpdateInspection]
 * in their `ProjectModuleOperationProvider` EP implementation to return `true`. Then, create a new
 * implementation of this abstract class that provides an appropriate [getVersionPsiElement] and
 * register it in the plugin.xml manifest.
 *
 * Note that this inspection follows the "only stable" inspection settings.
 *
 * @see ProjectModuleOperationProvider.usesSharedPackageUpdateInspection
 * @see PackageSearchDependencyUpgradeQuickFix
 */
abstract class PackageUpdateInspection : LocalInspectionTool() {

    @JvmField
    var onlyStable: Boolean = true

    @JvmField
    var enableRangeWarning: Boolean = true

    var excludeList: MutableList<String> = mutableListOf()

    companion object {

        private fun isMavenNotation(notation: String) = notation.split(":").size == 2

        private fun isExcluded(packageIdentifier: PackageIdentifier, exclusionRule: String): Boolean {
            val (groupId, artifactId) = packageIdentifier.rawValue.split(":")
            val (exclusionGroupId, exclusionArtifactId) = exclusionRule.split(":")

            return when {
                exclusionGroupId == "*" -> artifactId == exclusionArtifactId
                groupId == exclusionGroupId -> exclusionArtifactId == "*" || artifactId == exclusionArtifactId
                else -> false
            }
        }

        private fun isRange(version: String) = version.any { !it.isLetter() && !it.isDigit() && it != '_' && it != '.' && it != '-' }
    }

    override fun createOptionsPanel(): JPanel {
        val panel = MultipleCheckboxOptionsPanel(this)

        val injectionListTable = ListEditForm("", PackageSearchBundle.message("packagesearch.inspection.upgrade.excluded.dependencies"), excludeList)

        panel.addCheckbox(PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.filter.onlyStable"), ::onlyStable.name)
        panel.addCheckbox(PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.filter.rangeWarning"), ::enableRangeWarning.name)
        panel.addGrowing(injectionListTable.contentPanel)

        return panel
    }

    protected abstract fun getVersionPsiElement(file: PsiFile, dependency: UnifiedDependency): PsiElement?

    final override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (!shouldCheckFile(file)) {
            return null
        }

        val project = file.project
        val fileModule = ModuleUtil.findModuleForFile(file)
        if (fileModule == null) {
            thisLogger().warn("Inspecting file belonging to an unknown module")
            return null
        }

        val problemsHolder = ProblemsHolder(manager, file, isOnTheFly)

        if (enableRangeWarning) {
            project.packageSearchProjectService.dependenciesByModuleStateFlow.value
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

                    problemsHolder.registerProblem(psiElement, message, ProblemHighlightType.WEAK_WARNING)
                }
        }

        project.packageSearchProjectService.packageUpgradesStateFlow.value
            .getPackagesToUpgrade(onlyStable).upgradesByModule[fileModule]
            ?.filter { isNotExcluded(it.packageModel.identifier) }
            ?.filter { it.computeUpgradeOperationsForSingleModule.isNotEmpty() }
            ?.forEach { (packageModel, usageInfo,
                targetVersion, precomputedOperations) ->

                val currentVersion = usageInfo.version
                val scope = usageInfo.scope
                val unifiedDependency = packageModel.toUnifiedDependency(currentVersion, scope)
                val versionElement = tryDoing { getVersionPsiElement(file, unifiedDependency) } ?: return@forEach

                problemsHolder.registerProblem(
                    versionElement,
                    PackageSearchBundle.message(
                        "packagesearch.inspection.upgrade.description",
                        packageModel.identifier.rawValue,
                        targetVersion.originalVersion.displayName
                    ),
                    LocalQuickFixOnPsiElement(
                        element = versionElement,
                        familyName = PackageSearchBundle.message("packagesearch.quickfix.upgrade.family"),
                        text = PackageSearchBundle.message(
                            "packagesearch.quickfix.upgrade.action",
                            packageModel.identifier.rawValue,
                            targetVersion.originalVersion.displayName
                        ),
                        isHighPriority = true
                    ) {
                        NotifyingOperationExecutor(this).executeOperations(precomputedOperations)
                    },
                    LocalQuickFixOnPsiElement(
                        element = versionElement,
                        familyName = PackageSearchBundle.message("packagesearch.quickfix.upgrade.exclude.family"),
                        text = PackageSearchBundle.message(
                            "packagesearch.quickfix.upgrade.exclude.action",
                            packageModel.identifier.rawValue
                        ),
                        isHighPriority = false
                    ) {
                        excludeList.add(packageModel.identifier.rawValue)
                        ProjectInspectionProfileManager.getInstance(project).fireProfileChanged()
                    }
                )
            }

        return problemsHolder.resultsArray
    }

    private fun shouldCheckFile(file: PsiFile): Boolean {
        if (!file.project.packageSearchProjectService.isAvailable) return false

        val provider = ProjectModuleOperationProvider.forProjectPsiFileOrNull(file.project, file)
            ?.takeIf { it.usesSharedPackageUpdateInspection() }
            ?: return false

        return provider.hasSupportFor(file.project, file)
    }

    private fun isNotExcluded(packageIdentifier: PackageIdentifier) =
        excludeList.filter { isMavenNotation(it) }.none { isExcluded(packageIdentifier, it) }

    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING
}

@Suppress("FunctionName")
internal fun LocalQuickFixOnPsiElement(
    element: PsiElement,
    @Nls familyName: String,
    @Nls text: String,
    isHighPriority: Boolean,
    action: Project.() -> Unit
): LocalQuickFix = if (isHighPriority) object : LocalQuickFixOnPsiElement(element), HighPriorityAction {
    override fun getFamilyName() = familyName
    override fun getText() = text
    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) =
        project.action()
} else object : LocalQuickFixOnPsiElement(element), LowPriorityAction {
    override fun getFamilyName() = familyName
    override fun getText() = text
    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) =
        project.action()
}
