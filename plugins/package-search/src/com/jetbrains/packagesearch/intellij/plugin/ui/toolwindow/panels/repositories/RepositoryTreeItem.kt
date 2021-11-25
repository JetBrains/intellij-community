package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.repositories

import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.RepositoryModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.RepositoryUsageInfo
import java.awt.datatransfer.StringSelection
import java.util.Locale

internal sealed class RepositoryTreeItem {

    abstract fun toSimpleIdentifier(): String

    data class Repository(val repositoryModel: RepositoryModel) : RepositoryTreeItem(), DataProvider, CopyProvider {

        override fun toSimpleIdentifier(): String =
            "GROUP:${repositoryModel.id}:${repositoryModel.name}:${repositoryModel.url}".lowercase(Locale.getDefault())

        private fun getTextForCopy() = buildString {
            repositoryModel.id?.let { appendLine("  ${PackageSearchBundle.message("packagesearch.repository.copyableInfo.id", it)}") }
            repositoryModel.name?.let { appendLine("  ${PackageSearchBundle.message("packagesearch.repository.copyableInfo.name", it)}") }
            repositoryModel.url?.let { appendLine("  ${PackageSearchBundle.message("packagesearch.repository.copyableInfo.url", it)}") }
        }

        override fun getData(dataId: String) = when {
            PlatformDataKeys.COPY_PROVIDER.`is`(dataId) -> this
            else -> null
        }

        override fun performCopy(dataContext: DataContext) =
            CopyPasteManager.getInstance().setContents(StringSelection(getTextForCopy()))

        override fun isCopyVisible(dataContext: DataContext) = true
        override fun isCopyEnabled(dataContext: DataContext) = true
    }

    data class Module(val usageInfo: RepositoryUsageInfo) : RepositoryTreeItem() {

        override fun toSimpleIdentifier(): String = usageInfo.projectModule.name.lowercase(Locale.ROOT)
    }
}
