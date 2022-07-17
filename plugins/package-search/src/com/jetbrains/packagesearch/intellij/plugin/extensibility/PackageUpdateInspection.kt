/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ui.ListEditForm
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel
import com.intellij.openapi.module.Module
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.intentions.PackageSearchDependencyUpgradeQuickFix
import com.jetbrains.packagesearch.intellij.plugin.tryDoing
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageIdentifier
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.NotifyingOperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectService
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
abstract class PackageUpdateInspection : AbstractPackageUpdateInspectionCheck() {

    @JvmField
    var onlyStable: Boolean = true

    @JvmField
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

        panel.addCheckbox(PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.filter.onlyStable"), ::onlyStable.name)
        panel.addGrowing(injectionListTable.contentPanel)

        return panel
    }

    override fun ProblemsHolder.checkFile(file: PsiFile, fileModule: Module) {
        file.project.packageSearchProjectService.packageUpgradesStateFlow.value
            .getPackagesToUpgrade(onlyStable)
            .upgradesByModule[fileModule]
            ?.filter { isNotExcluded(it.packageModel.identifier) }
            ?.filter { it.computeUpgradeOperationsForSingleModule.isNotEmpty() }
            ?.forEach { (packageModel, usageInfo,
                targetVersion, precomputedOperations) ->

                val versionElement = tryDoing {
                    usageInfo.declarationIndexInBuildFile
                        ?.let { selectPsiElementIndex(it) }
                        ?.let { PsiUtil.getElementAtOffset(file, it) }
                } ?: return@forEach

                registerProblem(
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
                        ProjectInspectionProfileManager.getInstance(file.project).fireProfileChanged()
                    }
                )
            }
    }

    private fun isNotExcluded(packageIdentifier: PackageIdentifier) =
        excludeList.filter { isMavenNotation(it) }.none { isExcluded(packageIdentifier, it) }

    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING
}

