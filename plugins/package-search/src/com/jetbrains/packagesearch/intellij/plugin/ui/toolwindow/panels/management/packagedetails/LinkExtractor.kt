package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packagedetails

import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2LinkedFile
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Package
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Scm

internal class LinkExtractor(private val standardV2Package: StandardV2Package) {

    fun scm(): InfoLink.ScmRepository? {
        val scm = standardV2Package.scm ?: return null

        val (scmUrl, isGitHub) = extractScmUrl(scm)
        return when {
            isGitHub -> InfoLink.ScmRepository.GitHub(scmUrl, standardV2Package.gitHub?.stars)
            else -> InfoLink.ScmRepository.Generic(scmUrl)
        }
    }

    private fun extractScmUrl(scm: StandardV2Scm): ScmUrl {
        val scmUrl = scm.url
        val isGitHub = scmUrl.contains("github.com", true)

        val normalizedUrl = if (isGitHub) {
            // Try to extract the GitHub URL from the SCM URL, since it looks like a GitHub URL
            scmUrl.replace("((?:ssh|https)://)?git@github.com".toRegex(RegexOption.IGNORE_CASE), "https://github.com")
                .replace("github.com:", "github.com/")
                .trim()
        } else {
            scmUrl.trim()
        }

        return ScmUrl(normalizedUrl, isGitHub)
    }

    private data class ScmUrl(val url: String, val isGitHub: Boolean)

    fun projectWebsite(): InfoLink.ProjectWebsite? {
        if (standardV2Package.url == null) return null

        return InfoLink.ProjectWebsite(standardV2Package.url)
    }

    fun documentation(): InfoLink.Documentation? {
        if (standardV2Package.gitHub?.communityProfile?.documentationUrl == null) return null

        return InfoLink.Documentation(standardV2Package.gitHub.communityProfile.documentationUrl)
    }

    fun readme(): InfoLink.Readme? {
        if (standardV2Package.gitHub?.communityProfile?.files?.readme == null) return null

        val readmeUrl = standardV2Package.gitHub.communityProfile.files.readme.htmlUrl
            ?: standardV2Package.gitHub.communityProfile.files.readme.url
        return InfoLink.Readme(readmeUrl)
    }

    fun licenses(): List<InfoLink.License> {
        if (standardV2Package.licenses == null) return emptyList()

        val otherLicenses = standardV2Package.licenses.otherLicenses?.map { it.asLicenseInfoLink() }
            ?: emptyList()

        return listOf(standardV2Package.licenses.mainLicense.asLicenseInfoLink()) + otherLicenses
    }

    private fun StandardV2LinkedFile.asLicenseInfoLink() = InfoLink.License(
        url = htmlUrl ?: url,
        licenseName = name
    )
}
