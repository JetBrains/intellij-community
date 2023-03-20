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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.repositories

import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageSearchModule
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.RepositoryModel
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

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun performCopy(dataContext: DataContext) =
            CopyPasteManager.getInstance().setContents(StringSelection(getTextForCopy()))

        override fun isCopyVisible(dataContext: DataContext) = true
        override fun isCopyEnabled(dataContext: DataContext) = true
    }

    data class Module(val module: PackageSearchModule) : RepositoryTreeItem() {

        override fun toSimpleIdentifier(): String = module.name.lowercase(Locale.ROOT)
    }
}
