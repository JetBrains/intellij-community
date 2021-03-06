package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.left

import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.NlsContexts.Label
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.ui.JBEmptyBorder
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.ui.RiderUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchDependency
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import javax.swing.JLabel
import javax.swing.JPanel

sealed class PackagesSmartItem {

    object Fake : PackagesSmartItem() {

        val panel = JPanel(BorderLayout()).apply {
            background = RiderUI.MAIN_BG_COLOR
        }
    }

    class Package(val meta: PackageSearchDependency) : PackagesSmartItem(), DataProvider, CopyProvider {

        fun getData(dataId: String, projectModule: ProjectModule?): Any? {
            val module = projectModule ?: meta.installationInformation.firstOrNull()?.projectModule
            val information = meta.installationInformation.find { it.projectModule == module }

            return when {
                CommonDataKeys.VIRTUAL_FILE.`is`(dataId) -> module?.buildFile
                CommonDataKeys.NAVIGATABLE_ARRAY.`is`(dataId) -> information?.let {
                    arrayOf(it.projectModule.getNavigatableDependency(meta.groupId, meta.artifactId, it.installedVersion))
                }
                else -> getData(dataId)
            }
        }

        override fun getData(dataId: String): Any? = when {
            PlatformDataKeys.COPY_PROVIDER.`is`(dataId) -> this
            else -> null
        }

        override fun performCopy(dataContext: DataContext) {
            CopyPasteManager.getInstance().setContents(StringSelection(getTextForCopy()))
        }

        override fun isCopyVisible(dataContext: DataContext) = true
        override fun isCopyEnabled(dataContext: DataContext) = true

        private fun getTextForCopy(): String {
            val builder = StringBuffer("${meta.groupId}/${meta.artifactId}")

            if (meta.installationInformation.any()) {
                builder.appendLine()
                builder.append("  ${PackageSearchBundle.message("packagesearch.package.copiableInfo.installedVersions")} : ")
                builder.append(meta.installationInformation.map { it.installedVersion }
                    .distinct()
                    .joinToString("; ") {
                        if (it.isNotBlank()) it else PackageSearchBundle.message("packagesearch.package.copiableInfo.empty")
                    })
            }

            meta.remoteInfo?.versions?.let { versions ->
                if (versions.any()) {
                    builder.appendLine()
                    builder.append("  ${PackageSearchBundle.message("packagesearch.package.copiableInfo.availableVersions")} ")
                    builder.append(versions.joinToString("; ") { it.version })
                }
            }

            meta.remoteInfo?.gitHub?.let { gitHub ->
                builder.appendLine()
                builder.append("  ")
                builder.append(PackageSearchBundle.message("packagesearch.package.copiableInfo.githubStats"))
                builder.append(" ")
                builder.append(PackageSearchBundle.message("packagesearch.package.copiableInfo.githubStats.stars", gitHub.stars))
                builder.append(" ")
                builder.append(PackageSearchBundle.message("packagesearch.package.copiableInfo.githubStats.forks", gitHub.forks))
            }

            meta.remoteInfo?.stackOverflowTags?.tags?.let { tags ->
                if (tags.any()) {
                    builder.appendLine()
                    builder.append("  ${PackageSearchBundle.message("packagesearch.package.copiableInfo.stackOverflowTags")} ")
                    builder.append(tags.joinToString("; ") { "${it.tag} (${it.count})" })
                }
            }

            return builder.toString()
        }
    }

    class Header(@Label defaultTitle: String) : PackagesSmartItem() {

        private val titleLabel = JLabel(defaultTitle).apply {
            foreground = RiderUI.GRAY_COLOR
            border = JBEmptyBorder(0, 0, 0, 8)
        }

        private val progressIcon = JLabel(AnimatedIcon.Default())
            .apply {
                isVisible = false
            }

        private val headerLinks = mutableListOf<HyperlinkLabel>()

        fun addHeaderLink(link: HyperlinkLabel) {
            headerLinks.add(link)
        }

        var visible = true

        val panel
            get() = if (visible) {
                RiderUI.borderPanel(RiderUI.SectionHeaderBackgroundColor) {
                    RiderUI.setHeight(this, RiderUI.SmallHeaderHeight)
                    border = JBEmptyBorder(3, 0, 0, 18)

                    add(RiderUI.flowPanel(RiderUI.SectionHeaderBackgroundColor) {
                        layout = FlowLayout(FlowLayout.LEFT, 6, 0)

                        add(titleLabel)
                        add(progressIcon)
                    }, BorderLayout.WEST)

                    if (headerLinks.any()) {
                        val linksPanel = RiderUI.flowPanel(RiderUI.SectionHeaderBackgroundColor) {
                            border = JBEmptyBorder(-3, 0, 0, -8)

                            headerLinks.forEach { add(it) }
                        }

                        add(linksPanel, BorderLayout.EAST)
                    }
                }
            } else {
                Fake.panel
            }

        var title: String
            @Label get() = titleLabel.text
            set(@Label value) {
                titleLabel.text = value
            }

        fun setProgressVisibility(value: Boolean) {
            if (value) {
                visible = true // make sure header is visible when showing progress
            }

            // Show/hide progress icon
            progressIcon.isVisible = value
        }
    }
}
