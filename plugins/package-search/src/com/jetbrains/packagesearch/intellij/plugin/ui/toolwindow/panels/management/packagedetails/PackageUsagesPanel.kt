package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packagedetails

import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.pom.Navigatable
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.fus.FUSGroupIds
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.util.HtmlEditorPane
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import javax.swing.BoxLayout

internal class PackageUsagesPanel : HtmlEditorPane() {

    private val linkActionsMap = mutableMapOf<String, Navigatable?>()

  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    border = emptyBorder(top = 8)
    background = PackageSearchUI.UsualBackgroundColor
  }

    override fun onLinkClicked(anchor: String) {
        val navigatable = linkActionsMap[anchor] ?: return
        if (!navigatable.canNavigate()) return
        navigatable.navigate(true)
        PackageSearchEventsLogger.logDetailsLinkClick(FUSGroupIds.DetailsLinkTypes.PackageUsages)
    }

    fun display(packageModel: PackageModel.Installed) {
        clear()

        val chunks = mutableListOf<HtmlChunk>()
        chunks += HtmlChunk.p().addText(PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.usages.caption"))
        chunks += HtmlChunk.ul().children(
            packageModel.usageInfo.mapIndexed { index, usageInfo ->
                val anchor = "#$index"
                linkActionsMap[anchor] = usageInfo.projectModule.navigatableDependency(
                    packageModel.groupId,
                    packageModel.artifactId,
                    usageInfo.version
                )

                HtmlChunk.li().child(
                    HtmlChunk.link(anchor, usageInfo.projectModule.name)
                )
            }
        )
        setBody(chunks)
    }

    fun clear() {
        clearBody()
        linkActionsMap.clear()
    }
}
