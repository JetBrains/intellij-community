package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
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
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.computeActionsFor
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.logWarn
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectService
import com.jetbrains.packagesearch.intellij.plugin.util.parallelForEach
import com.jetbrains.packagesearch.intellij.plugin.util.toUnifiedDependency
import kotlinx.coroutines.runBlocking
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
    }

    override fun createOptionsPanel(): JPanel {
        val panel = MultipleCheckboxOptionsPanel(this)

        val injectionListTable = ListEditForm("", PackageSearchBundle.message("packagesearch.inspection.upgrade.excluded.dependencies"), excludeList)

        panel.addCheckbox(PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.filter.onlyStable"), "onlyStable")
        panel.addGrowing(injectionListTable.contentPanel)

        return panel
    }

    protected abstract fun getVersionPsiElement(file: PsiFile, dependency: UnifiedDependency): PsiElement?

    final override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (!shouldCheckFile(file)) {
            return null
        }

        val project = file.project
        val service = project.packageSearchProjectService

        val fileModule = ModuleUtil.findModuleForFile(file)
        if (fileModule == null) {
            thisLogger().warn("Inspecting file belonging to an unknown module")
            return null
        }

        val moduleModel = service.moduleModelsStateFlow.value
            .find { fileModule.isTheSameAs(it.projectModule.nativeModule) }

        if (moduleModel == null) {
            thisLogger().warn("Trying to upgrade something for an unknown module")
            return null
        }

        val availableUpdates = service.packageUpgradesStateFlow.value
            .getPackagesToUpgrade(onlyStable)
            .upgradesByModule[fileModule]
            ?.filter { isNotExcluded(it.packageModel.identifier) }
            ?: return null

        val problemsHolder = ProblemsHolder(manager, file, isOnTheFly)
        runBlocking {
            availableUpdates.parallelForEach { packageUpdateInfo ->
                val currentVersion = packageUpdateInfo.usageInfo.version
                val scope = packageUpdateInfo.usageInfo.scope
                val unifiedDependency = packageUpdateInfo.packageModel.toUnifiedDependency(currentVersion, scope)
                val versionElement = tryDoing { getVersionPsiElement(file, unifiedDependency) } ?: return@parallelForEach
                if (versionElement.containingFile != file) return@parallelForEach

                val targetModules = TargetModules.One(moduleModel)
                val allKnownRepositories = service.allInstalledKnownRepositoriesFlow.value

                val packageOperations = project.lifecycleScope.computeActionsFor(
                    packageModel = packageUpdateInfo.packageModel,
                    targetModules = targetModules,
                    knownRepositoriesInTargetModules = allKnownRepositories.filterOnlyThoseUsedIn(targetModules),
                    onlyStable = onlyStable
                )

                val identifier = packageUpdateInfo.packageModel.identifier
                if (!packageOperations.canUpgradePackage) {
                    logWarn { "Expecting to have upgrade actions for package ${identifier.rawValue} to $targetModules" }
                    return@parallelForEach
                }

                problemsHolder.registerProblem(
                    versionElement,
                    PackageSearchBundle.message(
                        "packagesearch.inspection.upgrade.description",
                        packageUpdateInfo.targetVersion.originalVersion.displayName
                    ),
                    PackageSearchDependencyUpgradeQuickFix(
                        element = versionElement,
                        identifier = identifier,
                        targetVersion = packageUpdateInfo.targetVersion.originalVersion,
                        operations = packageOperations.primaryOperations.await()
                    ),
                    LocalQuickFix(
                        PackageSearchBundle.message(
                            "packagesearch.quickfix.upgrade.exclude",
                            identifier.rawValue
                        )
                    ) {
                        excludeList.add(identifier.rawValue)
                        ProjectInspectionProfileManager.getInstance(project).fireProfileChanged()
                    }
                )
            }
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

internal fun LocalQuickFix(@Nls familyName: String, action: Project.(ProblemDescriptor) -> Unit) = object : LocalQuickFix {
    override fun getFamilyName() = familyName
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) = project.action(descriptor)
}
