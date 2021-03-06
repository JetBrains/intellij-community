package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.repositories

import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchRepository
import java.awt.datatransfer.StringSelection

abstract class RepositoryItem(val meta: PackageSearchRepository) : DataProvider, CopyProvider {

    abstract fun toSimpleIdentifier(): String

    private fun getTextForCopy(): String {
        val builder = StringBuffer()

        meta.projectModule?.let { builder.appendln(it.getFullName()) }
        meta.id?.let { builder.appendln("  ${PackageSearchBundle.message("packagesearch.repository.copiableInfo.id", it)}") }
        meta.name?.let { builder.appendln("  ${PackageSearchBundle.message("packagesearch.repository.copiableInfo.name", it)}") }
        meta.url?.let { builder.appendln("  ${PackageSearchBundle.message("packagesearch.repository.copiableInfo.url", it)}") }

        return builder.toString()
    }

    override fun getData(dataId: String): Any? = when {
        PlatformDataKeys.COPY_PROVIDER.`is`(dataId) -> this
        else -> null
    }

    override fun performCopy(dataContext: DataContext) =
        CopyPasteManager.getInstance().setContents(StringSelection(getTextForCopy()))

    override fun isCopyVisible(dataContext: DataContext) = true
    override fun isCopyEnabled(dataContext: DataContext) = true

    class Group(meta: PackageSearchRepository) : RepositoryItem(meta) {

        override fun toSimpleIdentifier(): String = "GROUP:${meta.id}:${meta.name}:${meta.url}".toLowerCase()
    }

    class Module(meta: PackageSearchRepository) : RepositoryItem(meta) {

        override fun toSimpleIdentifier(): String = "${meta.projectModule?.getFullName()}:${meta.id}:${meta.name}:${meta.url}".toLowerCase()
    }
}
