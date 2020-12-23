package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.right

import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Scm
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.InfoLink

fun extractScmUrl(scm: StandardV2Scm?): ScmUrl? {
    if (scm == null) return null

    val (type, url) = when {
        scm.url != null -> {
            val scmUrl = scm.url
            val type = if (scmUrl.contains("github.com", true)) ScmUrl.Type.GITHUB else ScmUrl.Type.GENERIC

            val normalizedUrl = if (type == ScmUrl.Type.GITHUB) {
                // Try to extract the GitHub URL from the SCM URL, since it looks like a GitHub URL
                scmUrl.replace("((?:ssh|https)://)?git@github.com".toRegex(RegexOption.IGNORE_CASE), "https://github.com")
                    .replace("github.com:", "github.com/")
            } else {
                scmUrl
            }

            type to normalizedUrl
        }
        else -> return null
    }

    return ScmUrl(url, type)
}

data class ScmUrl(val url: String, val type: Type) {

    enum class Type(val linkKey: InfoLink) {
        GENERIC(InfoLink.SCM),
        GITHUB(InfoLink.GITHUB)
    }
}
