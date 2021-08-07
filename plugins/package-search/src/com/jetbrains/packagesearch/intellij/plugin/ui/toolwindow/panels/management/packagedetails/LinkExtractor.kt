package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packagedetails

import com.jetbrains.packagesearch.api.v2.ApiStandardPackage

internal class LinkExtractor(private val standardV2Package: ApiStandardPackage) {

    fun scm(): InfoLink.ScmRepository? {
        val scm = standardV2Package.scm ?: return null

        val (scmUrl, isGitHub) = extractScmUrl(scm)
        return when {
            isGitHub -> InfoLink.ScmRepository.GitHub(scmUrl, standardV2Package.gitHub?.stars)
            else -> InfoLink.ScmRepository.Generic(scmUrl)
        }
    }

    private fun extractScmUrl(scm: ApiStandardPackage.ApiScm): ScmUrl {
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

    fun projectWebsite() = standardV2Package.url?.let { InfoLink.ProjectWebsite(it) }

    fun documentation() = standardV2Package.gitHub?.communityProfile?.documentation?.let { InfoLink.Documentation(it) }

    fun readme() = standardV2Package.gitHub?.communityProfile?.files?.readme?.let { InfoLink.Readme(it.htmlUrl ?: it.url) }

    fun licenses(): List<InfoLink.License> {
        val licenses = mutableListOf<InfoLink.License>()
        standardV2Package.licenses?.mainLicense?.let { licenses.add(it.asLicenseInfoLink()) }
        standardV2Package.licenses?.otherLicenses?.map { it.asLicenseInfoLink() }?.let { licenses.addAll(it) }
        return licenses
    }

    private fun ApiStandardPackage.ApiLinkedFile.asLicenseInfoLink() = InfoLink.License(
        url = htmlUrl ?: url,
        licenseName = name
    )
}
