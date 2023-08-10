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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packagedetails

import org.jetbrains.packagesearch.api.v2.ApiStandardPackage

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
