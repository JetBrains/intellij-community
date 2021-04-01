package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packagedetails

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import org.jetbrains.annotations.Nls
import java.net.MalformedURLException
import java.net.URL

internal sealed class InfoLink(
    @Nls open val displayName: String,
    @Nls open val displayNameCapitalized: String
) {

    abstract val url: String

    fun hasBrowsableUrl(): Boolean = try {
        val parsedUrl = URL(url)
        parsedUrl.protocol.equals("http", ignoreCase = true) ||
            parsedUrl.protocol.equals("https", ignoreCase = true)
    } catch (e: MalformedURLException) {
        false
    }

    data class ProjectWebsite(
        @NlsSafe override val url: String
    ) : InfoLink(
        PackageSearchBundle.message("packagesearch.ui.toolwindow.link.projectSite"),
        PackageSearchBundle.message("packagesearch.ui.toolwindow.link.projectSite.capitalized")
    )

    data class Documentation(
        @NlsSafe override val url: String
    ) : InfoLink(
        PackageSearchBundle.message("packagesearch.ui.toolwindow.link.documentation"),
        PackageSearchBundle.message("packagesearch.ui.toolwindow.link.documentation.capitalized")
    )

    data class Readme(
        @NlsSafe override val url: String
    ) : InfoLink(
        PackageSearchBundle.message("packagesearch.ui.toolwindow.link.readme"),
        PackageSearchBundle.message("packagesearch.ui.toolwindow.link.readme.capitalized")
    )

    data class License(
        @NlsSafe override val url: String,
        @NlsSafe val licenseName: String?
    ) : InfoLink(
        PackageSearchBundle.message("packagesearch.ui.toolwindow.link.license"),
        PackageSearchBundle.message("packagesearch.ui.toolwindow.link.license.capitalized")
    )

    sealed class ScmRepository(
        @Nls override val displayName: String,
        @Nls override val displayNameCapitalized: String
    ) : InfoLink(displayName, displayNameCapitalized) {

        data class GitHub(
            override val url: String,
            val stars: Int?
        ) : ScmRepository(
            PackageSearchBundle.message("packagesearch.ui.toolwindow.link.github"),
            PackageSearchBundle.message("packagesearch.ui.toolwindow.link.github.capitalized")
        )

        data class Generic(
            override val url: String
        ) : ScmRepository(
            PackageSearchBundle.message("packagesearch.ui.toolwindow.link.scm"),
            PackageSearchBundle.message("packagesearch.ui.toolwindow.link.scm.capitalized")
        )
    }
}
