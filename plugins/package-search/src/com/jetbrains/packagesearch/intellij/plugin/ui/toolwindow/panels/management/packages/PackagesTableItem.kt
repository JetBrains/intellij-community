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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiPackageModel
import com.jetbrains.packagesearch.intellij.plugin.util.VersionNameComparator
import java.awt.datatransfer.StringSelection

internal sealed class PackagesTableItem<T : PackageModel> : DataProvider, CopyProvider {

    val packageModel: T
        get() = uiPackageModel.packageModel

    abstract val uiPackageModel: UiPackageModel<T>

    abstract val allScopes: List<PackageScope>

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
                        .sortedWith(VersionNameComparator)
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

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun isCopyVisible(dataContext: DataContext) = true

    override fun isCopyEnabled(dataContext: DataContext) = true

    data class InstalledPackage(
        override val uiPackageModel: UiPackageModel.Installed,
        override val allScopes: List<PackageScope>
    ) : PackagesTableItem<PackageModel.Installed>() {

        init {
            require(allScopes.isNotEmpty()) { "An installed package must have at least one installed scope" }
        }

        override fun additionalCopyText() = buildString {
            if (packageModel.usagesByModule.isEmpty()) return@buildString

            appendLine()
            append("${PackageSearchBundle.message("packagesearch.package.copyableInfo.installedVersions")} ")
            append(
                packageModel.usagesByModule.values.flatten()
                    .map { it.declaredVersion }
                    .distinct()
                    .joinToString(", ")
                    .removeSuffix(", ")
            )
        }
    }

    data class InstallablePackage(
        override val uiPackageModel: UiPackageModel.SearchResult,
        override val allScopes: List<PackageScope>
    ) : PackagesTableItem<PackageModel.SearchResult>() {

        init {
            require(allScopes.isNotEmpty()) { "An installable package must have at least one available scope" }
        }

        override fun additionalCopyText() = ""
    }
}
