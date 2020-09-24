package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle

enum class InfoLink(val displayName: String) {
    PROJECT_SITE(PackageSearchBundle.message("packagesearch.ui.toolwindow.link.projectSite")),
    DOCUMENTATION(PackageSearchBundle.message("packagesearch.ui.toolwindow.link.documentation")),
    README(PackageSearchBundle.message("packagesearch.ui.toolwindow.link.readme")),
    CODE_OF_CONDUCT(PackageSearchBundle.message("packagesearch.ui.toolwindow.link.codeOfConduct")),
    GITHUB(PackageSearchBundle.message("packagesearch.ui.toolwindow.link.github")),
    SCM(PackageSearchBundle.message("packagesearch.ui.toolwindow.link.scm"))
}
