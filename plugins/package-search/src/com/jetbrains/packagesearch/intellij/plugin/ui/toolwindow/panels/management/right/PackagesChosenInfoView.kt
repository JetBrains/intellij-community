package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.right

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsContexts.LinkLabel
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.api.model.StackOverflowTag
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Author
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2GitHub
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2LinkedFile
import com.jetbrains.packagesearch.intellij.plugin.normalizeSpace
import com.jetbrains.packagesearch.intellij.plugin.ui.RiderUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.InfoLink
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.InfoLink.GITHUB
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchDependency
import com.jetbrains.packagesearch.intellij.plugin.ui.updateAndRepaint
import icons.PackageSearchIcons
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.util.*
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.SwingConstants

class PackagesChosenInfoView {

    private var packageMeta: PackageSearchDependency? = null

    private val infoContentPanel = RiderUI.boxPanel {
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private val rowPanelLabelBorder = JBEmptyBorder(0, 2, 3, 0)

    val panel = RiderUI.boxPanel {
        alignmentX = Component.LEFT_ALIGNMENT
        border = JBUI.Borders.empty(10, 0, 10, 20)
        add(infoContentPanel)
    }

    private val refreshUISync = Object()

    init {
        refreshUI()
    }

    fun refreshUI(newMeta: PackageSearchDependency?) {
        packageMeta = newMeta
        refreshUI()
    }

    private fun refreshUI() {
        synchronized(refreshUISync) {
            val meta = packageMeta ?: return

            refreshInfo(meta)
            panel.updateAndRepaint()
        }
    }

    private fun refreshInfo(meta: PackageSearchDependency) {
        infoContentPanel.removeAll()

        if (meta.remoteInfo == null) return

        // Fetch all links on package
        val links = meta.getAllLinks()

        // Render info
        val miscPanels = ArrayList<JPanel?>()

        meta.remoteInfo?.authors?.let { authors -> renderAuthorsIfAny(authors, miscPanels) }

        meta.remoteInfo?.gitHub?.let { gitHub -> renderGitHubInfo(miscPanels, links, gitHub) }

        val linkComponents = mutableListOf<Component>()
        if (links.any()) {
            linkComponents.addAll(links.map {
                createHyperlinkLabel(it.key.displayName, it.value)
            })
        }
        meta.remoteInfo?.stackOverflowTags?.tags?.let { tags ->
            renderStackOverflowTagsIfAny(tags, linkComponents)
        }
        if (linkComponents.any()) {
            miscPanels.add(createRowPanel(linkComponents))
        }

        val licence = meta.remoteInfo?.licenses?.mainLicense
            ?: meta.remoteInfo?.gitHub?.communityProfile?.files?.license
        licence?.let {
            renderLicenseIfValid(it, miscPanels)
        }

        miscPanels.filterNotNull().toList().forEach { infoContentPanel.add(it) }
    }

    private fun renderAuthorsIfAny(authors: List<StandardV2Author>, miscPanels: ArrayList<JPanel?>) {
        if (authors.isEmpty()) return

        val authorsString = authors.filterNot { it.name.isNullOrBlank() }
            .map { it.name.normalizeSpace() }
            .joinToString { it ?: "" }

        val byString = PackageSearchBundle.message("packagesearch.terminology.by.someone", authorsString)

        miscPanels.add(
            createRowPanel(
                listOf(
                    JBLabel("<html><b>$byString</b></html>").apply {
                        border = rowPanelLabelBorder
                        setAllowAutoWrapping(true)
                    })
            )
        )
    }

    private fun renderGitHubInfo(miscPanels: ArrayList<JPanel?>, links: MutableMap<InfoLink, String>, gitHub: StandardV2GitHub) {
        miscPanels.add(
            createRowPanel(
                listOf(
                    createHyperlinkLabel(GITHUB.displayName, links.remove(GITHUB)),
                    JBLabel(
                        PackageSearchBundle.message("packagesearch.ui.toolwindow.github.stars", gitHub.stars),
                        AllIcons.Plugins.Rating, SwingConstants.LEFT
                    ),
                    JBLabel(
                        PackageSearchBundle.message("packagesearch.ui.toolwindow.github.forks", gitHub.forks),
                        AllIcons.Vcs.Branch, SwingConstants.LEFT
                    )
                )
            )
        )
    }

    private fun renderStackOverflowTagsIfAny(tags: List<StackOverflowTag>, linkComponents: MutableList<Component>) {
        if (tags.isEmpty()) return

        linkComponents.addAll(tags.map {
          createHyperlinkLabel(
            PackageSearchBundle.message("packagesearch.ui.toolwindow.stackover.tagWithCount", it.tag, it.count),
            PackageSearchBundle.message("packagesearch.wellknown.url.stackoverflow", it.tag),
            PackageSearchIcons.StackOverflow
          )
        })
    }

    private fun renderLicenseIfValid(linkedFile: StandardV2LinkedFile, miscPanels: ArrayList<JPanel?>) {
        val licenseUrl = linkedFile.htmlUrl ?: linkedFile.url
        val licenseName = linkedFile.name ?: PackageSearchBundle.message("packagesearch.terminology.license.information")

        if (!licenseUrl.isNullOrEmpty()) {
            miscPanels.add(
                createRowPanel(
                    listOf(
                        createHyperlinkLabel(licenseName, licenseUrl)
                    )
                )
            )
        }
    }

    fun show() {
        panel.isVisible = true
    }

    fun hide() {
        panel.isVisible = false
    }

    private fun createHyperlinkLabel(@LinkLabel text: String, url: String?, icon: Icon? = null): Component {
        return if (!url.isNullOrEmpty() &&
            (url.startsWith("http://") || url.startsWith("https://"))) {

            HyperlinkLabel(text).apply {
                setHyperlinkTarget(url)
                if (icon != null) setIcon(icon)
            }
        } else {
            JBLabel(text).apply {
                border = rowPanelLabelBorder
                if (icon != null) setIcon(icon)
            }
        }
    }

    private fun createRowPanel(components: List<Component>, showAnyway: Boolean = false): JPanel? {
        if (components.isEmpty() && !showAnyway) {
            return null
        }

        @Suppress("MagicNumber") // Gotta love Swing APIs
        if (components.size == 1) {
            return RiderUI.boxPanel {
                border = JBUI.Borders.empty(0, 4, 3, 0)

                alignmentX = Component.LEFT_ALIGNMENT
                minimumSize = Dimension(1, 1)
                maximumSize = Dimension(Short.MAX_VALUE.toInt(), 180)

                components.forEach {
                    add(it)
                }
            }
        } else {
            return RiderUI.borderPanel { layout = FlowLayout(FlowLayout.LEFT, 4, 0) }.apply {
                border = JBUI.Borders.empty(0, 0, 3, 0)

                alignmentX = Component.LEFT_ALIGNMENT
                minimumSize = Dimension(1, 1)
                maximumSize = Dimension(Short.MAX_VALUE.toInt(), 180)

                components.forEach {
                    add(it)
                }
            }
        }
    }
}
