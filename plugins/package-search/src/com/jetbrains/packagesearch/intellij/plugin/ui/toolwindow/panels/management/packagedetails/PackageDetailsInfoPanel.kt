package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packagedetails

import com.jetbrains.packagesearch.api.v2.ApiStandardPackage
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.fus.FUSGroupIds
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.normalizeWhitespace
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.KnownRepositories
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.updateAndRepaint
import com.jetbrains.packagesearch.intellij.plugin.ui.util.ScaledPixels
import com.jetbrains.packagesearch.intellij.plugin.ui.util.compensateForHighlightableComponentMarginLeft
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
import java.awt.Component
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

@Suppress("MagicNumber") // Swing dimension constants
internal class PackageDetailsInfoPanel : JPanel() {

    @ScaledPixels private val maxRowHeight = 180.scaled()

    private val noDataLabel = PackageSearchUI.createLabel {
        foreground = PackageSearchUI.GRAY_COLOR
        text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.noData")
            .withHtmlStyling(wordWrap = true)
    }.withMaxHeight(maxRowHeight)

    private val descriptionLabel = PackageSearchUI.createLabel()

    private val repositoriesLabel = PackageSearchUI.createLabel()
        .withMaxHeight(maxRowHeight)

    private val gitHubPanel = GitHubInfoPanel()
        .withMaxHeight(maxRowHeight)

    private val licensesLinkLabel = PackageSearchUI.createLabelWithLink()
        .withMaxHeight(maxRowHeight)

    private val projectWebsiteLinkLabel = PackageSearchUI.createLabelWithLink()

    private val documentationLinkLabel = PackageSearchUI.createLabelWithLink()

    private val readmeLinkLabel = PackageSearchUI.createLabelWithLink()

    private val kotlinPlatformsPanel = PackageKotlinPlatformsPanel()

    private val usagesPanel = PackageUsagesPanel()

    private val authorsLabel = PackageSearchUI.createLabel()

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
                .fill().gap()
        )
        background = PackageSearchUI.UsualBackgroundColor
        alignmentX = Component.LEFT_ALIGNMENT

        val horizontalBorder = 12
        border = emptyBorder(left = horizontalBorder, bottom = 20, right = horizontalBorder)

        add(noDataLabel, CC().wrap())
        add(repositoriesLabel, CC().wrap())
        add(descriptionLabel, CC().wrap())
        add(gitHubPanel, CC().wrap())
        add(licensesLinkLabel, CC().compensateForHighlightableComponentMarginLeft().wrap())
        add(projectWebsiteLinkLabel, CC().compensateForHighlightableComponentMarginLeft().wrap())
        add(documentationLinkLabel, CC().compensateForHighlightableComponentMarginLeft().wrap())
        add(readmeLinkLabel, CC().compensateForHighlightableComponentMarginLeft().wrap())
        add(kotlinPlatformsPanel, CC().wrap())
        add(usagesPanel, CC().wrap())
        add(authorsLabel, CC().wrap())
    }

    internal data class ViewModel(
        val packageModel: PackageModel,
        val selectedVersion: PackageVersion,
        val allKnownRepositories: KnownRepositories.All
    )

    fun display(viewModel: ViewModel) {
        clearPanelContents()
        if (viewModel.packageModel.remoteInfo == null) {
            return
        }

        noDataLabel.isVisible = false

        displayDescriptionIfAny(viewModel.packageModel.remoteInfo)

        val selectedVersionInfo = viewModel.packageModel.remoteInfo
            .versions.find { it.version == viewModel.selectedVersion.versionName }
        displayRepositoriesIfAny(selectedVersionInfo, viewModel.allKnownRepositories)

        displayAuthorsIfAny(viewModel.packageModel.remoteInfo.authors)

        val linkExtractor = LinkExtractor(viewModel.packageModel.remoteInfo)
        displayGitHubInfoIfAny(linkExtractor.scm())
        displayLicensesIfAny(linkExtractor.licenses())
        displayProjectWebsiteIfAny(linkExtractor.projectWebsite())
        displayDocumentationIfAny(linkExtractor.documentation())
        displayReadmeIfAny(linkExtractor.readme())
        displayKotlinPlatformsIfAny(viewModel.packageModel.remoteInfo)
        displayUsagesIfAny(viewModel.packageModel)

        updateAndRepaint()
        (parent as JComponent).updateAndRepaint()
    }

    private fun clearPanelContents() {
        noDataLabel.isVisible = true

        descriptionLabel.isVisible = false
        repositoriesLabel.isVisible = false
        authorsLabel.isVisible = false
        gitHubPanel.isVisible = false
        licensesLinkLabel.isVisible = false
        projectWebsiteLinkLabel.isVisible = false
        documentationLinkLabel.isVisible = false
        readmeLinkLabel.isVisible = false
        kotlinPlatformsPanel.isVisible = false
        usagesPanel.isVisible = false

        kotlinPlatformsPanel.clear()
        usagesPanel.clear()

        updateAndRepaint()
    }

    private fun displayDescriptionIfAny(remoteInfo: ApiStandardPackage) {
        @Suppress("HardCodedStringLiteral") // Comes from the API, it's @NlsSafe
        val description = remoteInfo.description
        if (description.isNullOrBlank() || description == remoteInfo.name) {
            descriptionLabel.isVisible = false
            return
        }

        descriptionLabel.isVisible = true
        descriptionLabel.text = description.normalizeWhitespace().withHtmlStyling(wordWrap = true)
    }

    private fun displayRepositoriesIfAny(
        selectedVersionInfo: ApiStandardPackage.ApiStandardVersion?,
        allKnownRepositories: KnownRepositories.All
    ) {
        if (selectedVersionInfo == null) {
            repositoriesLabel.isVisible = false
            return
        }

        val repositoryNames = selectedVersionInfo.repositoryIds
            .mapNotNull { repoId -> allKnownRepositories.findById(repoId)?.displayName }
            .filterNot { it.isBlank() }
        val repositoryNamesToDisplay = repositoryNames.joinToString()

        repositoriesLabel.text = if (repositoryNames.size == 1) {
            PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.repository", repositoryNamesToDisplay)
        } else {
            PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.repositories", repositoryNamesToDisplay)
        }.withHtmlStyling(wordWrap = true)

        repositoriesLabel.isVisible = true
    }

    private fun displayAuthorsIfAny(authors: List<ApiStandardPackage.ApiAuthor>?) {
        if (authors.isNullOrEmpty()) {
            authorsLabel.isVisible = false
            return
        }

        val authorNames = authors.filterNot { it.name.isNullOrBlank() }
            .map { it.name.normalizeWhitespace() }

        val authorsString = if (authorNames.size == 1) {
            PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.author", authorNames.joinToString())
        } else {
            PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.authors", authorNames.joinToString())
        }

        authorsLabel.text = authorsString.withHtmlStyling(wordWrap = true)
        authorsLabel.isVisible = true
    }

    private fun displayGitHubInfoIfAny(scmInfoLink: InfoLink.ScmRepository?) {
        if (scmInfoLink !is InfoLink.ScmRepository.GitHub || !scmInfoLink.hasBrowsableUrl()) {
            gitHubPanel.isVisible = false
            return
        }

        gitHubPanel.isVisible = true
        gitHubPanel.text = scmInfoLink.displayNameCapitalized
        gitHubPanel.url = scmInfoLink.url
        gitHubPanel.stars = scmInfoLink.stars
    }

    private fun displayLicensesIfAny(licenseInfoLink: List<InfoLink.License>) {
        // TODO move this to a separate component, handle multiple licenses
        val mainLicense = licenseInfoLink.firstOrNull()
        if (mainLicense == null || !mainLicense.hasBrowsableUrl()) {
            licensesLinkLabel.isVisible = false
            return
        }

        licensesLinkLabel.isVisible = true
        licensesLinkLabel.url = mainLicense.url
        licensesLinkLabel.setDisplayText(
            if (mainLicense.licenseName != null) {
                PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.license", mainLicense.licenseName)
            } else {
                mainLicense.displayNameCapitalized
            }
        )
        licensesLinkLabel.urlClickedListener = {
            PackageSearchEventsLogger.logDetailsLinkClick(FUSGroupIds.DetailsLinkTypes.License)
        }
    }

    private fun displayProjectWebsiteIfAny(projectWebsiteLink: InfoLink.ProjectWebsite?) {
        if (projectWebsiteLink == null || !projectWebsiteLink.hasBrowsableUrl()) {
            projectWebsiteLinkLabel.isVisible = false
            return
        }

        projectWebsiteLinkLabel.isVisible = true
        projectWebsiteLinkLabel.url = projectWebsiteLink.url
        projectWebsiteLinkLabel.setDisplayText(projectWebsiteLink.displayNameCapitalized)
        projectWebsiteLinkLabel.urlClickedListener = {
            PackageSearchEventsLogger.logDetailsLinkClick(FUSGroupIds.DetailsLinkTypes.ProjectWebsite)
        }
    }

    private fun displayDocumentationIfAny(documentationLink: InfoLink.Documentation?) {
        if (documentationLink == null || !documentationLink.hasBrowsableUrl()) {
            documentationLinkLabel.isVisible = false
            return
        }

        documentationLinkLabel.isVisible = true
        documentationLinkLabel.url = documentationLink.url
        documentationLinkLabel.setDisplayText(documentationLink.displayNameCapitalized)
        documentationLinkLabel.urlClickedListener = {
            PackageSearchEventsLogger.logDetailsLinkClick(FUSGroupIds.DetailsLinkTypes.Documentation)
        }
    }

    private fun displayReadmeIfAny(readmeLink: InfoLink.Readme?) {
        if (readmeLink == null || !readmeLink.hasBrowsableUrl()) {
            readmeLinkLabel.isVisible = false
            return
        }

        readmeLinkLabel.isVisible = true
        readmeLinkLabel.url = readmeLink.url
        readmeLinkLabel.setDisplayText(readmeLink.displayNameCapitalized)
        readmeLinkLabel.urlClickedListener = {
            PackageSearchEventsLogger.logDetailsLinkClick(FUSGroupIds.DetailsLinkTypes.Readme)
        }
    }

    private fun displayKotlinPlatformsIfAny(packageDetails: ApiStandardPackage?) {
        val isKotlinMultiplatform = packageDetails?.mpp != null

        if (isKotlinMultiplatform && packageDetails?.platforms?.isNotEmpty() == true) {
            kotlinPlatformsPanel.display(packageDetails.platforms ?: emptyList())
            kotlinPlatformsPanel.isVisible = true
        } else {
            kotlinPlatformsPanel.clear()
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
