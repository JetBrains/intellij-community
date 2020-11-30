package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.util.text.VersionComparatorUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.looksLikeGradleVariable
import com.jetbrains.packagesearch.intellij.plugin.ui.RiderUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.InstallationInformation.Companion.DEFAULT_SCOPE
import icons.PackageSearchIcons
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JButton

class PackageOperationTargetScope(val scopesModel: CollectionComboBoxModel<String>) {

    @NlsSafe
    fun getSelectedScope(): String = scopesModel.selected!!
}

class PackageOperationTarget(
    val projectModule: ProjectModule,
    val projectModuleTitle: String,
    val packageSearchDependency: PackageSearchDependency,
    val version: String,
    val installedScope: String?,
    val targetScope: PackageOperationTargetScope
) {

    private val isDefaultInstalledScope = installedScope == DEFAULT_SCOPE

    val installedScopeIfNotDefault: String? = if (!isDefaultInstalledScope) installedScope else null

    fun getApplyOperation(targetVersion: String) = when {
        looksLikeGradleVariable(version) || looksLikeGradleVariable(targetVersion) -> null

        targetVersion.isNotEmpty() && version != targetVersion -> PackageOperation.resolve(
            packageSearchDependency.identifier,
            version,
            targetVersion
        )

        version.isBlank() && (installedScope.isNullOrEmpty() || isDefaultInstalledScope) && targetVersion.isBlank() ->
            PackageOperation.install(packageSearchDependency.identifier, version)

        else -> null
    }

    fun getRemoveOperation() = if (version.isEmpty() && (installedScope.isNullOrEmpty() || isDefaultInstalledScope)) {
        null
    } else {
        PackageOperation.remove(packageSearchDependency.identifier, version)
    }

    override fun toString() = PackageSearchBundle.message(
        "packagesearch.packageoperation.target.toString",
        projectModuleTitle, packageSearchDependency.identifier, version
    )
}

enum class PackageOperationType {
    INSTALL,
    UPGRADE,
    DOWNGRADE,
    REMOVE
}

class PackageOperation(
  @Nls val title: String,
  @Nls val description: String,
  @Nls val htmlDescription: String,
  val icon: Icon,
  val packageOperationType: PackageOperationType
) {

    companion object {

        fun install(identifier: String, version: String) =
          PackageOperation(
            PackageSearchBundle.message("packagesearch.packageoperation.install.title"),
            PackageSearchBundle.message("packagesearch.packageoperation.install.description", identifier, version),
            PackageSearchBundle.message("packagesearch.packageoperation.install.htmlDescription", identifier, version),
            PackageSearchIcons.Operations.Install,
            PackageOperationType.INSTALL
          )

        fun remove(identifier: String, version: String) =
          PackageOperation(
            PackageSearchBundle.message("packagesearch.packageoperation.remove.title"),
            PackageSearchBundle.message("packagesearch.packageoperation.remove.description", identifier, version),
            PackageSearchBundle.message("packagesearch.packageoperation.remove.htmlDescription", identifier, version),
            PackageSearchIcons.Operations.Remove,
            PackageOperationType.REMOVE
          )

        fun resolve(identifier: String, oldVersion: String, newVersion: String) = when {
            looksLikeGradleVariable(oldVersion) || looksLikeGradleVariable(newVersion) -> null

            oldVersion.isBlank() && newVersion.isBlank() -> install(
                identifier,
                newVersion
            )

            oldVersion.isBlank() && newVersion.isNotBlank() -> install(
                identifier,
                newVersion
            )

            oldVersion.isNotBlank() && newVersion.isBlank() -> remove(
                identifier,
                oldVersion
            )

            VersionComparatorUtil.compare(oldVersion, newVersion) < 0 -> PackageOperation(
              PackageSearchBundle.message("packagesearch.packageoperation.upgrade.title"),
              PackageSearchBundle.message("packagesearch.packageoperation.upgrade.description", identifier, newVersion),
              PackageSearchBundle.message("packagesearch.packageoperation.upgrade.htmlDescription", identifier, newVersion),
              PackageSearchIcons.Operations.Upgrade,
              PackageOperationType.UPGRADE
            )

            VersionComparatorUtil.compare(oldVersion, newVersion) > 0 -> PackageOperation(
              PackageSearchBundle.message("packagesearch.packageoperation.downgrade.title"),
              PackageSearchBundle.message("packagesearch.packageoperation.downgrade.description", identifier, newVersion),
              PackageSearchBundle.message("packagesearch.packageoperation.downgrade.htmlDescription", identifier, newVersion),
              PackageSearchIcons.Operations.Downgrade,
              PackageOperationType.DOWNGRADE
            )

            VersionComparatorUtil.compare(oldVersion, newVersion) == 0 -> null

            else -> null
        }
    }

    fun toButton(buttonSize: Int = 32, action: (PackageOperation) -> Unit): JButton {
        val op = this
        val size = Dimension(JBUI.scale(buttonSize), JBUI.scale(buttonSize))
        return JButton().apply {
            this.icon = op.icon
            this.disabledIcon = IconLoader.getDisabledIcon(op.icon)
            minimumSize = size
            maximumSize = size
            preferredSize = size
            border = JBUI.Borders.empty(0)
            margin = JBInsets(0, 0, 0, 0)
            toolTipText = op.htmlDescription
            background = RiderUI.UsualBackgroundColor
            addActionListener { action(op) }
        }
    }
}

data class ExecutablePackageOperation(
    val operation: PackageOperation,
    val operationTarget: PackageOperationTarget,
    val targetVersion: String?
)
