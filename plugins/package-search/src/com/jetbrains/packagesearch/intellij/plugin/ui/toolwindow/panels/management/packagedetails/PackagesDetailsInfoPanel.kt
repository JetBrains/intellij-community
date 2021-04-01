package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packagedetails

import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Author
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Package
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Version
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.RepositoryModel
import com.jetbrains.packagesearch.intellij.plugin.ui.updateAndRepaint
import com.jetbrains.packagesearch.intellij.plugin.ui.util.ScaledPixels
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import com.jetbrains.packagesearch.intellij.plugin.ui.util.noInsets
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaledAsString
import com.jetbrains.packagesearch.intellij.plugin.ui.util.skipInvisibleComponents
import com.jetbrains.packagesearch.intellij.plugin.ui.util.withHtmlStyling
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.apache.commons.lang3.StringUtils
import java.awt.Component
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

@Suppress("MagicNumber") // Thanks Swing...
internal class PackagesDetailsInfoPanel : JPanel() {

    @ScaledPixels private val maxRowHeight = 180.scaled()

    private val noDataLabel = PackageSearchUI.createLabel {
        foreground = PackageSearchUI.GRAY_COLOR
        text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.noData")
            .withHtmlStyling(wordWrap = true)
    }.withMaxHeight(maxRowHeight)

    private val repositoriesLabel = PackageSearchUI.createLabel()
        .withMaxHeight(maxRowHeight)

    private val authorsLabel = PackageSearchUI.createLabel()
        .withMaxHeight(maxRowHeight)

    private val gitHubPanel = GitHubInfoPanel()
        .withMaxHeight(maxRowHeight)

    private val licensesLinkLabel = PackageSearchUI.createLabelWithLink()
        .withMaxHeight(maxRowHeight)

    private val projectWebsiteLinkLabel = PackageSearchUI.createLabelWithLink()

    private val documentationLinkLabel = PackageSearchUI.createLabelWithLink()

    private val readmeLinkLabel = PackageSearchUI.createLabelWithLink()

    private val kotlinPlatformsPanel = DependencyKotlinPlatformsPanel()

    private val usagesPanel = DependencyUsagesPanel()

    init {
        layout = MigLayout(
            LC().fillX()
                .noInsets()
                .skipInvisibleComponents()
                .gridGap("0", 8.scaledAsString()),
            AC().grow(), // One column only
            AC().fill().gap() // All rows are filling all available width
                .fill().gap()
                .fill().gap()
                .fill().gap()
                .fill().gap()
                .fill().gap()
                .fill().gap()
        )
        background = PackageSearchUI.UsualBackgroundColor
        alignmentX = Component.LEFT_ALIGNMENT

        @ScaledPixels val horizontalBorder = 12.scaled()
        border = emptyBorder(left = horizontalBorder, bottom = 20.scaled(), right = horizontalBorder)

        fun CC.compensateForHighlightableComponentMarginLeft() = pad(0, (-2).scaled(), 0, 0)

        add(noDataLabel, CC().wrap())
        add(repositoriesLabel, CC().wrap())
        add(authorsLabel, CC().wrap())
        add(gitHubPanel, CC().wrap())
        add(licensesLinkLabel, CC().compensateForHighlightableComponentMarginLeft().wrap())
        add(projectWebsiteLinkLabel, CC().compensateForHighlightableComponentMarginLeft().wrap())
        add(documentationLinkLabel, CC().compensateForHighlightableComponentMarginLeft().wrap())
        add(readmeLinkLabel, CC().compensateForHighlightableComponentMarginLeft().wrap())
        add(kotlinPlatformsPanel, CC().wrap())
        add(usagesPanel, CC().wrap())
    }

    fun display(
        packageModel: PackageModel,
        selectedVersion: PackageVersion,
        installedKnownRepositories: List<RepositoryModel>
    ) {
        if (packageModel.remoteInfo == null) {
            clearPanelContents()
            return
        }

        noDataLabel.isVisible = false

        val selectedVersionInfo = packageModel.remoteInfo.versions.find { it.version == selectedVersion.versionName }
        displayRepositoriesIfAny(selectedVersionInfo, installedKnownRepositories)

        displayAuthorsIfAny(packageModel.remoteInfo.authors)

        val linkExtractor = LinkExtractor(packageModel.remoteInfo)
        displayGitHubInfoIfAny(linkExtractor.scm())
        displayLicensesIfAny(linkExtractor.licenses())
        displayProjectWebsiteIfAny(linkExtractor.projectWebsite())
        displayDocumentationIfAny(linkExtractor.documentation())
        displayReadmeIfAny(linkExtractor.readme())
        displayKotlinPlatformsIfAny(packageModel.remoteInfo)
        displayUsagesIfAny(packageModel)

        updateAndRepaint()
        (parent as JComponent).updateAndRepaint()
    }

    private fun clearPanelContents() {
        noDataLabel.isVisible = true

        repositoriesLabel.isVisible = false
        authorsLabel.isVisible = false
        gitHubPanel.isVisible = false
        licensesLinkLabel.isVisible = false
        projectWebsiteLinkLabel.isVisible = false
        documentationLinkLabel.isVisible = false
        readmeLinkLabel.isVisible = false

        kotlinPlatformsPanel.clear()
        usagesPanel.clear()

        updateAndRepaint()
    }

    private fun displayRepositoriesIfAny(
        selectedVersionInfo: StandardV2Version?,
        knownRepositories: List<RepositoryModel>
    ) {
        if (selectedVersionInfo == null) {
            repositoriesLabel.isVisible = false
            return
        }

        val repositoryNames = selectedVersionInfo.repositoryIds
            .mapNotNull { repoId -> knownRepositories.find { it.id == repoId }?.displayName }
            .joinToString()

        repositoriesLabel.text = if (selectedVersionInfo.repositoryIds.size == 1) {
            PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.repository", repositoryNames)
        } else {
            PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.repositories", repositoryNames)
        }.withHtmlStyling(wordWrap = true)

        repositoriesLabel.isVisible = true
    }

    private fun displayAuthorsIfAny(authors: List<StandardV2Author>?) {
        if (authors.isNullOrEmpty()) {
            authorsLabel.isVisible = false
            return
        }

        val authorNames = authors.filterNot { it.name.isNullOrBlank() }
            .map { StringUtils.normalizeSpace(it.name) }

        val authorsString = if (authorNames.size == 1) {
            PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.author", authorNames.joinToString())
        } else {
            PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.authors", authorNames.joinToString())
        }

        authorsLabel.text = authorsString.withHtmlStyling(wordWrap = true)
        authorsLabel.isVisible = true
    }

    private fun displayGitHubInfoIfAny(scmInfoLink: InfoLink.ScmRepository?) {
        if (scmInfoLink !is InfoLink.ScmRepository.GitHub) {
            gitHubPanel.isVisible = false
            return
        }

        gitHubPanel.text = scmInfoLink.displayNameCapitalized
        gitHubPanel.url = scmInfoLink.url
        gitHubPanel.stars = scmInfoLink.stars
        gitHubPanel.isVisible = true
    }

    private fun displayLicensesIfAny(licenseInfoLink: List<InfoLink.License>) {
        if (licenseInfoLink.isEmpty()) {
            licensesLinkLabel.isVisible = false
            return
        }

        // TODO move this to a separate component, handle multiple licenses
        val mainLicense = licenseInfoLink.first()
        licensesLinkLabel.url = mainLicense.url
        licensesLinkLabel.setDisplayText(
            if (mainLicense.licenseName != null) {
                PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.license", mainLicense.licenseName)
            } else {
                mainLicense.displayNameCapitalized
            }
        )

        licensesLinkLabel.isVisible = true
    }

    private fun displayProjectWebsiteIfAny(projectWebsiteLink: InfoLink.ProjectWebsite?) {
        if (projectWebsiteLink == null) {
            projectWebsiteLinkLabel.isVisible = false
            return
        }

        projectWebsiteLinkLabel.isVisible = true
        projectWebsiteLinkLabel.url = projectWebsiteLink.url
        projectWebsiteLinkLabel.setDisplayText(projectWebsiteLink.displayNameCapitalized)
    }

    private fun displayDocumentationIfAny(documentationLink: InfoLink.Documentation?) {
        if (documentationLink == null) {
            documentationLinkLabel.isVisible = false
            return
        }

        documentationLinkLabel.isVisible = true
        documentationLinkLabel.url = documentationLink.url
        documentationLinkLabel.setDisplayText(documentationLink.displayNameCapitalized)
    }

    private fun displayReadmeIfAny(readmeLink: InfoLink.Readme?) {
        if (readmeLink == null) {
            readmeLinkLabel.isVisible = false
            return
        }

        readmeLinkLabel.isVisible = true
        readmeLinkLabel.url = readmeLink.url
        readmeLinkLabel.setDisplayText(readmeLink.displayNameCapitalized)
    }

    private fun displayKotlinPlatformsIfAny(packageDetails: StandardV2Package?) {
        val isKotlinMultiplatform = packageDetails?.mpp != null

        if (isKotlinMultiplatform && packageDetails?.platforms?.isNotEmpty() == true) {
            kotlinPlatformsPanel.display(packageDetails.platforms)
            kotlinPlatformsPanel.isVisible = true
        } else {
            kotlinPlatformsPanel.isVisible = false
        }
    }

    private fun displayUsagesIfAny(packageModel: PackageModel) {
        if (packageModel is PackageModel.Installed) {
            usagesPanel.display(packageModel)
            usagesPanel.isVisible = true
        } else {
            usagesPanel.clear()
            usagesPanel.isVisible = false
        }
    }

    private fun <T : JComponent> T.withMaxHeight(@ScaledPixels maxHeight: Int): T {
        maximumSize = Dimension(Int.MAX_VALUE, maxHeight)
        return this
    }
}
