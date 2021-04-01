package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packagedetails

import com.intellij.pom.Navigatable
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.util.HtmlEditorPane
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaledEmptyBorder
import javax.swing.BoxLayout

internal class DependencyUsagesPanel : HtmlEditorPane() {

    private val linkActionsMap = mutableMapOf<String, Navigatable?>()

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = scaledEmptyBorder(top = 8)
        background = PackageSearchUI.UsualBackgroundColor
    }

    override fun onLinkClicked(anchor: String) {
        val navigatable = linkActionsMap[anchor] ?: return
        if (!navigatable.canNavigate()) return
        navigatable.navigate(true)
    }

    fun display(packageModel: PackageModel.Installed) {
        clear()

        val bodyString = buildString {
            append("<p>")
            append(PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.usages.caption"))
            append("</p><ul>")

            for ((index, usageInfo) in packageModel.usageInfo.withIndex()) {
                val anchor = "#$index"
                append("<li><a href=\"$anchor\">")
                append(usageInfo.projectModule.name)

                linkActionsMap[anchor] = usageInfo.projectModule.getNavigatableDependency(
                    packageModel.groupId,
                    packageModel.artifactId,
                    usageInfo.version
                )
                append("</li></a>")
            }
            append("</ul>")
        }
        setBody(bodyString)
    }

    fun clear() {
        setBody("")
        linkActionsMap.clear()
    }
}
