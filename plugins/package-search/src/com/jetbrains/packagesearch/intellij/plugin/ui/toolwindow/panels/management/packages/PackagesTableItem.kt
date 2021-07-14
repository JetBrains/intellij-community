package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.util.text.VersionComparatorUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.SelectedPackageModel
import java.awt.datatransfer.StringSelection

internal sealed class PackagesTableItem<T : PackageModel> : DataProvider, CopyProvider {

    val packageModel: T
        get() = selectedPackageModel.packageModel

    abstract val selectedPackageModel: SelectedPackageModel<T>

    protected open val handledDataKeys: List<DataKey<*>> = listOf(PlatformDataKeys.COPY_PROVIDER)

    fun canProvideDataFor(key: String) = handledDataKeys.any { it.`is`(key) }

    override fun getData(dataId: String): Any? = when {
        PlatformDataKeys.COPY_PROVIDER.`is`(dataId) -> this
        else -> null
    }

    override fun performCopy(dataContext: DataContext) =
        CopyPasteManager.getInstance().setContents(StringSelection(getTextForCopy(packageModel)))

    private fun getTextForCopy(packageModel: PackageModel) = buildString {
        appendLine("${packageModel.groupId}:${packageModel.artifactId}")

        append(additionalCopyText())

        packageModel.remoteInfo?.versions?.let { versions ->
            if (versions.any()) {
                appendLine()
                append("${PackageSearchBundle.message("packagesearch.package.copyableInfo.availableVersions")} ")
                append(
                    versions.map { it.version }
                        .distinct()
                        .sortedWith(VersionComparatorUtil.COMPARATOR)
                        .joinToString(", ")
                        .removeSuffix(", ")
                )
            }
        }

        packageModel.remoteInfo?.gitHub?.let { gitHub ->
            appendLine()
            append(PackageSearchBundle.message("packagesearch.package.copyableInfo.githubStats"))
            gitHub.stars?.let { ghStars ->
                append(" ")
                append(PackageSearchBundle.message("packagesearch.package.copyableInfo.githubStats.stars", ghStars))
            }
            gitHub.forks?.let { ghForks ->
                if (gitHub.stars != null) append(',')
                append(" ")
                append(PackageSearchBundle.message("packagesearch.package.copyableInfo.githubStats.forks", ghForks))
            }
        }

        packageModel.remoteInfo?.stackOverflow?.tags?.let { tags ->
            if (tags.any()) {
                appendLine()
                append("${PackageSearchBundle.message("packagesearch.package.copyableInfo.stackOverflowTags")} ")
                append(
                    tags.joinToString(", ") { "${it.tag} (${it.count})" }
                        .removeSuffix(", ")
                )
            }
        }
    }

    protected abstract fun additionalCopyText(): String

    override fun isCopyVisible(dataContext: DataContext) = true

    override fun isCopyEnabled(dataContext: DataContext) = true

    data class InstalledPackage(
        override val selectedPackageModel: SelectedPackageModel<PackageModel.Installed>,
        val installedScopes: List<PackageScope>,
        val defaultScope: PackageScope
    ) : PackagesTableItem<PackageModel.Installed>() {

        init {
            require(installedScopes.isNotEmpty()) { "An installed package must have at least one installed scope" }
        }

        override fun additionalCopyText() = buildString {
            if (packageModel.usageInfo.isEmpty()) return@buildString

            appendLine()
            append("${PackageSearchBundle.message("packagesearch.package.copyableInfo.installedVersions")} ")
            append(
                packageModel.usageInfo.map { it.version }
                    .distinct()
                    .joinToString(", ")
                    .removeSuffix(", ")
            )
        }
    }

    data class InstallablePackage(
        override val selectedPackageModel: SelectedPackageModel<PackageModel.SearchResult>,
        val availableScopes: List<PackageScope>,
        val defaultScope: PackageScope
    ) : PackagesTableItem<PackageModel.SearchResult>() {

        override fun additionalCopyText() = ""

        init {
            require(availableScopes.isNotEmpty()) { "A package must have at least one available scope" }
        }
    }
}
